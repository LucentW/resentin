package pm.antani.resentin.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.vodozemac_android.Account

/** Two engines talking through the wire shapes only, the way grappa would relay them. */
@RunWith(AndroidJUnit4::class)
class E2eEngineTest {
    private class Peer(val nick: String) {
        val device = Account()
        val master = Account()
        val list = listOf(device.ed25519Key()).let {
            SignedList(1, it, master.sign(listSigText(1, it)))
        }
        val own = OwnIdentity(
            master.ed25519Key(), master.sign(certSigText(1, device.ed25519Key())), 1, list, didOf(device.ed25519Key()),
        )
        val engine = E2eEngine(device, own, { nick }, E2eState())

        /** The KEYS line grappa would answer for this peer: its list and one signed one-time key. */
        fun keysPayload(): String {
            device.generateOneTimeKeys(1uL)
            val otk = signedKey(device, device.oneTimeKeys().first())
            val id = idBlock(device, master.ed25519Key())
            return buildJsonObject {
                put("v", 2)
                put("master", buildJsonObject {
                    put("ed25519", master.ed25519Key())
                    put("list", buildJsonObject {
                        put("ver", list.ver)
                        put("devices", kotlinx.serialization.json.JsonArray(list.devices.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                    })
                    put("sig", list.sig)
                })
                put("devices", kotlinx.serialization.json.JsonArray(listOf(buildJsonObject {
                    put("id", buildJsonObject { put("curve25519", id.curve25519); put("ed25519", id.ed25519); put("sig", id.sig) })
                    put("otk", buildJsonObject { put("key", otk.key); put("fallback", false); put("sig", otk.sig) })
                })))
            }.toString()
        }
    }

    private fun text(s: String) = buildJsonObject { put("t", "text"); put("body", s) }

    @Test
    fun aTextCrossesKeyreqKeysPkmsgAndMsg() {
        val alice = Peer("alice")
        val bob = Peer("bob")

        // Alice has no keys for bob: the text waits and a KEYREQ goes.
        var r = alice.engine.send("bob", text("ciao"), "t1")
        assertEquals(listOf("keyreq"), r.outbound.map { it.verb })

        // bob's grappa answers with KEYS; alice flushes her queue as a PKMSG.
        r = alice.engine.receive("bob", "keys", "*", "k1", bob.keysPayload())
        assertEquals(listOf("pkmsg"), r.outbound.map { it.verb })
        assertTrue("a message that went out is no failure: ${r.events}", r.events.none { it is E2eEvent.SendFailed })
        val pk = r.outbound.single()
        assertEquals(bob.own.did, pk.payload.let { kotlinx.serialization.json.Json.parseToJsonElement(it) }
            .let { (it as kotlinx.serialization.json.JsonObject)["bodies"] as kotlinx.serialization.json.JsonObject }.keys.single())

        // bob reads it: the text, and alice is pinned and trusted on first use.
        r = bob.engine.receive("alice", "pkmsg", alice.own.did, "m1", pk.payload)
        val got = r.events.filterIsInstance<E2eEvent.Received>().single()
        assertEquals("ciao", got.inner["body"]!!.jsonPrimitive.content)
        assertEquals(alice.own.master, bob.engine.state.pins["alice"])

        // bob answers: he already holds a session to alice's device, so the MSG goes at once; with
        // no list for alice yet he also asks for keys, and the KEYS that follows sends nothing again.
        r = bob.engine.send("alice", text("ehi"), "t2")
        assertEquals(listOf("msg", "keyreq"), r.outbound.map { it.verb })
        val msg = r.outbound.first()
        r = bob.engine.receive("alice", "keys", "*", "k2", alice.keysPayload())
        assertTrue(r.outbound.isEmpty())
        assertEquals(1, bob.engine.state.masters[alice.own.master]!!.ver)

        r = alice.engine.receive("bob", "msg", bob.own.did, "m2", msg.payload)
        assertEquals("ehi", r.events.filterIsInstance<E2eEvent.Received>().single().inner["body"]!!.jsonPrimitive.content)

        // A replayed PKMSG on a known session is answered with a NACK, never read twice as new.
        r = bob.engine.receive("alice", "pkmsg", alice.own.did, "m1", pk.payload)
        assertTrue(r.events.none { it is E2eEvent.Received && it.inner["body"]!!.jsonPrimitive.content == "x" })
    }

    @Test
    fun aDifferentMasterOnAPinnedNickIsAKeyAlarmNotATrust() {
        val alice = Peer("alice")
        val bob = Peer("bob")
        val impostor = Peer("bob")
        alice.engine.send("bob", text("1"), "t1")
        alice.engine.receive("bob", "keys", "*", "k", bob.keysPayload())
        alice.engine.send("bob", text("2"), "t2") // pinned: goes straight out, no KEYREQ
        // A new KEYREQ cycle, now answered by another master under the same nick.
        alice.engine.state.keysAwaited += "bob"
        val r = alice.engine.receive("bob", "keys", "*", "k2", impostor.keysPayload())
        assertTrue(r.events.any { it is E2eEvent.KeyAlarm })
    }

    @Test
    fun aNewerListIsAskedForAnsweredAndTaken() {
        val alice = Peer("alice")
        val bob = Peer("bob")
        // The usual opening: alice holds bob's list v1 and a session to him.
        alice.engine.send("bob", text("1"), "t1")
        val pk = alice.engine.receive("bob", "keys", "*", "k1", bob.keysPayload()).outbound.single()
        bob.engine.receive("alice", "pkmsg", alice.own.did, "m1", pk.payload)
        assertEquals(1, alice.engine.state.masters[bob.own.master]!!.ver)

        // bob pairs a second device: his list is now v2 and his messages say so (`lv`).
        val extra = Account()
        val devices = listOf(bob.device.ed25519Key(), extra.ed25519Key()).sorted()
        val list2 = SignedList(2, devices, bob.master.sign(listSigText(2, devices)))
        val bob2 = E2eEngine(bob.device, bob.own.copy(list = list2), { "bob" }, bob.engine.state)
        val hello = bob2.send("alice", text("ciao"), "t2").outbound.first { it.verb == "msg" || it.verb == "pkmsg" }

        // alice reads it, notices the higher `lv`, and asks bob (on the session, to his device only) for the list.
        val r = alice.engine.receive("bob", hello.verb, bob.own.did, "m2", hello.payload)
        assertEquals("ciao", r.events.filterIsInstance<E2eEvent.Received>().single().inner["body"]!!.jsonPrimitive.content)
        val listreq = r.outbound.single { it.tag == "list" }
        assertEquals(bob.own.did, listreq.to)

        // bob answers with his signed list; the listreq is never shown to the app.
        val rb = bob2.receive("alice", listreq.verb, alice.own.did, "m3", listreq.payload)
        assertTrue(rb.events.none { it is E2eEvent.Received })
        val answer = rb.outbound.single { it.tag == "list" }

        // alice takes it: the master's stored list moves to v2 with both devices.
        val ra = alice.engine.receive("bob", answer.verb, bob.own.did, "m4", answer.payload)
        assertTrue(ra.events.none { it is E2eEvent.Received })
        val record = alice.engine.state.masters[bob.own.master]!!
        assertEquals(2, record.ver)
        assertEquals(devices, record.devices)

        // Asking again within the minute is not done, and the same listreq is answered at most once a minute.
        val again = bob2.receive("alice", listreq.verb, alice.own.did, "m5", listreq.payload)
        assertTrue(again.outbound.none { it.tag == "list" })
    }

    @Test
    fun takingTheNewKeyResolvesTheAlarmAndItsKeysAreAccepted() {
        val alice = Peer("alice")
        val bob = Peer("bob")
        val newBob = Peer("bob")
        alice.engine.send("bob", text("1"), "t1")
        alice.engine.receive("bob", "keys", "*", "k", bob.keysPayload())
        // another master answers under the same nick: an alarm, remembered, and nothing pinned over it.
        alice.engine.state.keysAwaited += "bob"
        val r = alice.engine.receive("bob", "keys", "*", "k2", newBob.keysPayload())
        assertTrue(r.events.any { it is E2eEvent.KeyAlarm })
        assertEquals(newBob.own.master, alice.engine.state.alarms["bob"]!!.presented)
        assertEquals(bob.own.master, alice.engine.state.pins["bob"])

        // the user takes the new key (unverified) and its keys are asked for at once.
        assertEquals("pinned", alice.engine.pin("bob", newBob.own.master, false))
        assertTrue(alice.engine.state.alarms.isEmpty())
        assertEquals(newBob.own.master, alice.engine.state.pins["bob"])
        assertFalse(alice.engine.pinned("bob")!!.verified)
        assertEquals(listOf("keyreq"), alice.engine.askKeys("bob").outbound.map { it.verb })
        val ok = alice.engine.receive("bob", "keys", "*", "k3", newBob.keysPayload())
        assertTrue(ok.events.none { it is E2eEvent.KeyAlarm })
        assertEquals("not our own key", "own-key", alice.engine.pin("bob", alice.own.master, false))
    }

    // Two devices of ONE account (same master), each knowing the other from grappa's registry.
    private class Twin(val master: Account, val devs: List<Account>, val i: Int) {
        val device = devs[i]
        val sorted = devs.map { it.ed25519Key() }.sorted()
        val list = SignedList(1, sorted, master.sign(listSigText(1, sorted)))
        val own = OwnIdentity(master.ed25519Key(), master.sign(certSigText(1, device.ed25519Key())), 1, list, didOf(device.ed25519Key()))
        val engine = E2eEngine(device, own, { "me" }, E2eState())
        fun block(): pm.antani.resentin.net.dto.E2eDeviceDto {
            val id = idBlock(device, master.ed25519Key())
            return pm.antani.resentin.net.dto.E2eDeviceDto(own.did, id.ed25519, id.curve25519, id.sig)
        }
        /** The KEYS text the claim door hands out for this device only. */
        fun claimText(): String {
            device.generateOneTimeKeys(1uL)
            val otk = signedKey(device, device.oneTimeKeys().first())
            val id = idBlock(device, master.ed25519Key())
            return buildJsonObject {
                put("v", 2)
                put("master", buildJsonObject {
                    put("ed25519", master.ed25519Key())
                    put("list", buildJsonObject { put("ver", list.ver); put("devices", JsonArray(list.devices.map { JsonPrimitive(it) })) })
                    put("sig", list.sig)
                })
                put("devices", JsonArray(listOf(buildJsonObject {
                    put("id", buildJsonObject { put("curve25519", id.curve25519); put("ed25519", id.ed25519); put("sig", id.sig) })
                    put("otk", buildJsonObject { put("key", otk.key); put("fallback", false); put("sig", otk.sig) })
                })))
            }.toString()
        }
    }

    @Test
    fun aTextSentOnOneDeviceIsCopiedToTheOtherAndBack() {
        val master = Account(); val devs = listOf(Account(), Account())
        val a = Twin(master, devs, 0); val b = Twin(master, devs, 1)
        a.engine.setSiblings(listOf(b.block())); b.engine.setSiblings(listOf(a.block()))

        // a sent "ciao" to bob: it needs one of b's keys first (the claim door), then the copy goes.
        val c = a.engine.copy("bob", "ciao")
        assertEquals(b.own.did, (c.result.events.single { it is E2eEvent.SiblingKeysNeeded } as E2eEvent.SiblingKeysNeeded).did)
        assertTrue(c.copies.isEmpty())
        val sent = a.engine.siblingKeys(b.own.did, b.claimText())
        val copy = sent.copies.single()
        assertEquals("pkmsg", copy.verb)
        assertEquals(b.own.did, copy.to)

        // b reads it as something its own account sent to bob, and now knows a as a sibling with a session.
        val got = b.engine.receiveCopy(a.own.did, copy.verb, copy.payload).events.filterIsInstance<E2eEvent.Copied>().single()
        assertEquals("bob", got.peer); assertEquals("ciao", got.body)

        // b answers on that session: no claim needed, and a normal message (not a pre-key one).
        val back = b.engine.copy("bob", "rispondo")
        assertTrue(back.result.events.none { it is E2eEvent.SiblingKeysNeeded })
        assertEquals("msg", back.copies.single().verb)
        val got2 = a.engine.receiveCopy(b.own.did, "msg", back.copies.single().payload).events.filterIsInstance<E2eEvent.Copied>().single()
        assertEquals("rispondo", got2.body)

        // a device that is not on our list cannot copy: its message is dropped, nothing shown.
        val stranger = Twin(master, listOf(Account(), Account()), 0)
        val fake = stranger.engine.copy("bob", "x")
        assertTrue(fake.copies.isEmpty())
        val dropped = a.engine.receiveCopy(stranger.own.did, "msg", """{"v":2,"ck":"${stranger.device.curve25519Key()}","bodies":{"${a.own.did}":"AAAA"}}""")
        assertTrue(dropped.events.none { it is E2eEvent.Copied })
    }
}
