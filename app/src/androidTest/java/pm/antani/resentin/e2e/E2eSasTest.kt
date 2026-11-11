package pm.antani.resentin.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.vodozemac_android.Account

/** Two engines with a SAS each, wired through the wire shapes only. */
@RunWith(AndroidJUnit4::class)
class E2eSasTest {
    private class Node(val nick: String) {
        val device = Account()
        val master = Account()
        val list = listOf(device.ed25519Key()).let { SignedList(1, it, master.sign(listSigText(1, it))) }
        val own = OwnIdentity(master.ed25519Key(), master.sign(certSigText(1, device.ed25519Key())), 1, list, didOf(device.ed25519Key()))
        val engine = E2eEngine(device, own, { nick }, E2eState())
        val verifier = E2eVerifier(engine, engine.state)

        fun keysPayload(): String {
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

    private fun text(s: String) = buildJsonObject { put("t", "text"); put("body", s) }

    /** Carries every line from [from] to [to] and back until nothing is left to say; SAS inner messages go to the verifiers. */
    private fun pump(from: Node, to: Node, first: List<Outbound>) {
        var queue = first.map { Triple(from, to, it) }
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 200) {
            val next = mutableListOf<Triple<Node, Node, Outbound>>()
            for ((src, dst, o) in queue) {
                val r = dst.engine.receive(src.nick, o.verb, src.own.did, "m${guard}", o.payload)
                next += r.outbound.map { Triple(dst, src, it) }
                for (e in r.events.filterIsInstance<E2eEvent.Received>()) {
                    if ((e.inner["t"] as JsonPrimitive).content.startsWith("sas.")) dst.verifier.receive(src.nick, e.master, e.device, e.inner)
                }
                next += dst.verifier.take().outbound.map { Triple(dst, src, it) }
            }
            queue = next
        }
    }

    private fun opened(): Pair<Node, Node> {
        val alice = Node("alice"); val bob = Node("bob")
        alice.engine.send("bob", text("1"), "t1")
        val pk = alice.engine.receive("bob", "keys", "*", "k1", bob.keysPayload()).outbound.single()
        bob.engine.receive("alice", "pkmsg", alice.own.did, "m1", pk.payload)
        // bob learns alice's list too, so he trusts her device and can verify with it.
        bob.engine.send("alice", text("2"), "t2")
        bob.engine.receive("alice", "keys", "*", "k2", alice.keysPayload())
        return alice to bob
    }

    @Test
    fun bothSidesSeeTheSameEmojiAndEndVerified() {
        val (alice, bob) = opened()
        assertFalse(alice.engine.pinned("bob")!!.verified)

        alice.verifier.step("bob", "start"); pump(alice, bob, alice.verifier.take().outbound)
        assertEquals("asked", bob.verifier.view("alice")!!.phase)

        bob.verifier.step("alice", "accept"); pump(bob, alice, bob.verifier.take().outbound)
        val a = alice.verifier.view("bob")!!; val b = bob.verifier.view("alice")!!
        assertEquals("compare", a.phase); assertEquals("compare", b.phase)
        assertEquals(7, a.emoji!!.size)
        assertEquals(a.emoji, b.emoji)                       // the whole point: what the two users compare
        assertTrue(a.emoji!!.all { it in SAS_EMOJI.indices })

        alice.verifier.step("bob", "match"); pump(alice, bob, alice.verifier.take().outbound)
        bob.verifier.step("alice", "match"); pump(bob, alice, bob.verifier.take().outbound)

        assertTrue(alice.engine.pinned("bob")!!.verified)
        assertTrue(bob.engine.pinned("alice")!!.verified)
        assertEquals(null, alice.verifier.view("bob"))        // finished: nothing running
    }

    @Test
    fun aMismatchCancelsAndVerifiesNothing() {
        val (alice, bob) = opened()
        alice.verifier.step("bob", "start"); pump(alice, bob, alice.verifier.take().outbound)
        bob.verifier.step("alice", "accept"); pump(bob, alice, bob.verifier.take().outbound)
        assertNotNull(alice.verifier.view("bob")!!.emoji)

        alice.verifier.step("bob", "mismatch"); pump(alice, bob, alice.verifier.take().outbound)
        assertFalse(alice.engine.pinned("bob")!!.verified)
        assertFalse(bob.engine.pinned("alice")!!.verified)
        assertEquals(null, bob.verifier.view("alice"))        // bob heard the cancel
    }
}
