package pm.antani.resentin.e2e

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import uniffi.vodozemac_android.Account
import uniffi.vodozemac_android.Session
import uniffi.vodozemac_android.canonicalCurve25519
import uniffi.vodozemac_android.canonicalEd25519
import uniffi.vodozemac_android.preKeySessionId
import uniffi.vodozemac_android.sha256
import uniffi.vodozemac_android.verifyEd25519

// Port of the 1:1 core of cicchetto's engine.ts (wire v2): KEYREQ/KEYS, Olm sessions, PKMSG/MSG,
// NACK, trust on first use. A pure state machine: it does no I/O, it takes what arrived and returns
// events for the app and lines for grappa's send door. NOT ported yet (later phases): siblings and
// copies, SAS, t:list / t:listreq, order challenges, the line-budget check.

const val KEYREQ_RETRY_MS = 30_000L
const val OUTQ_TTL_MS = 90_000L
const val OUTQ_MAX = 16
const val REKEY_INTERVAL_MS = 60_000L
const val MAX_SENDS = 2
const val LISTREQ_INTERVAL_MS = 60_000L
const val LIST_TAG = "list"
const val MAX_SESSIONS_PER_PEER = 5
const val SEEN_SESSIONS_MAX = 10_000
const val WIRE_VERSION = 2
private const val BIND_PREFIX = "irc-e2e:v2:bind:"
private const val MAX_DEVICES = 4

/** The ASCII case fold of a nick (CASEMAPPING=ascii, grappa's `canonical_target`). */
fun foldNick(nick: String) = nick.map { if (it in 'A'..'Z') it + 32 else it }.joinToString("")

@Serializable
data class PeerDevice(
    val ed25519: String,
    val curve25519: String,
    val master: String,
    val did: String,
    /** A pre-key message built to us on that curve decrypted; an id block alone only claims it. */
    val proven: Boolean = false,
    val certVer: Int? = null,
)

@Serializable
data class PeerMaster(
    val nick: String,
    val verified: Boolean = false,
    val ver: Int = 0,
    val devices: List<String> = emptyList(),
    val revoked: List<String> = emptyList(),
    val revokedCurves: List<String> = emptyList(),
)

@Serializable
data class Outgoing(val id: String, val plaintext: String, val priority: String, val sends: Int, val tag: String)

@Serializable
data class Queued(val nick: String, val master: String, val only: String?, val done: List<String>, val msg: Outgoing, val at: Long)

@Serializable
data class SentDevice(val ed25519: String, val sessionId: String)

@Serializable
data class ListReq(val at: Long, val lv: List<Int> = emptyList())

@Serializable
data class Sent(val ref: String, val msgid: String?, val nick: String, val master: String, val devices: Map<String, SentDevice>, val msg: Outgoing, val at: Long)

/** Everything the engine remembers, stored with the account in one record. Keys by folded nick. */
@Serializable
data class E2eState(
    val masters: MutableMap<String, PeerMaster> = mutableMapOf(),
    val devices: MutableMap<String, PeerDevice> = mutableMapOf(),
    val pins: MutableMap<String, String> = mutableMapOf(),
    val sessions: MutableMap<String, List<String>> = mutableMapOf(),
    val seen: MutableList<String> = mutableListOf(),
    val outq: MutableMap<String, List<Queued>> = mutableMapOf(),
    val keyreqSentAt: MutableMap<String, Long> = mutableMapOf(),
    val keysAwaited: MutableSet<String> = mutableSetOf(),
    val rekeyAt: MutableMap<String, Long> = mutableMapOf(),
    val rekeying: MutableSet<String> = mutableSetOf(),
    val sent: MutableList<Sent> = mutableListOf(),
    var nextRef: Int = 1,
    /** Folded nick -> when a `listreq` last went to it, and for which list versions (`lv`). */
    val listreqAt: MutableMap<String, ListReq> = mutableMapOf(),
    /** `${folded nick} ${did}` -> when our list last went to that device (its `listreq`, or a body for a device our list does not name). */
    val listAnsweredAt: MutableMap<String, Long> = mutableMapOf(),
    /** The last inbox id processed, stored with what processing it changed. */
    var inbox: Long = 0,
) {
    /** A new device account: only the trust placed in others stays (their masters, devices, pins). */
    fun startOver() {
        sessions.clear(); seen.clear(); outq.clear(); keyreqSentAt.clear(); keysAwaited.clear()
        rekeyAt.clear(); rekeying.clear(); sent.clear(); nextRef = 1; inbox = 0
        listreqAt.clear(); listAnsweredAt.clear()
    }
}

sealed interface E2eEvent {
    data class Received(val nick: String, val master: String, val device: String, val verified: Boolean, val inner: JsonObject) : E2eEvent
    data class KeyAlarm(val nick: String, val pinned: String, val presented: String) : E2eEvent
    data class Undecryptable(val nick: String, val msgid: String, val reason: String) : E2eEvent
    data class Dropped(val nick: String, val reason: String, val verb: String?) : E2eEvent
    data class SendQueued(val nick: String, val queued: Int, val tag: String) : E2eEvent
    data class SendFailed(val nick: String, val reason: String, val tags: List<String>) : E2eEvent
    data class Rekeying(val nick: String, val device: String) : E2eEvent
}

/** One line for grappa's send door (`POST …/e2e/messages`). [ref] names it until grappa gives the msgid. */
data class Outbound(
    val target: String,
    val verb: String,
    val from: String,
    val to: String,
    val payload: String,
    val priority: String,
    val ref: String? = null,
    val tag: String? = null,
)

class Result(val events: MutableList<E2eEvent> = mutableListOf(), val outbound: MutableList<Outbound> = mutableListOf())

private val b64e = Base64.getEncoder().withoutPadding()
private val b64d = Base64.getDecoder()
private val json = Json { encodeDefaults = true }

private fun b64(bytes: ByteArray) = b64e.encodeToString(bytes)

/** Takes the [e2e_message] payload of one peer line; throws [PayloadError] on shape. */
class PayloadError(message: String) : Exception(message)

private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw PayloadError("missing $k")
private fun JsonObject.obj(k: String) = this[k] as? JsonObject ?: throw PayloadError("missing $k")

private fun fbOf(fromE: String, toE: String) =
    b64(sha256("$BIND_PREFIX$fromE:$toE".toByteArray()).copyOf(16))

private fun canon(f: () -> String, key: String) = runCatching(f).getOrNull() == key
private fun ed(key: String) = canon({ canonicalEd25519(key) }, key)
private fun curveOk(key: String) = canon({ canonicalCurve25519(key) }, key)

/** The inner JSON object of a plaintext, with the `fb` binding checked against [sender] (pre-key bodies). */
class E2eEngine(
    private val account: Account,
    private val own: OwnIdentity,
    private val ownNick: () -> String,
    val state: E2eState,
    private val now: () -> Long = System::currentTimeMillis,
) {
    // ── sending ──

    /** Sends [inner] (a typed JSON object, e.g. `{"t":"text","body":"…"}`) to [nick]'s devices. */
    fun send(nick: String, inner: JsonObject, tag: String): Result {
        val res = Result()
        dispatch(nick, inner, tag, res)
        return res
    }

    private fun dispatch(nick: String, inner: JsonObject, tag: String, res: Result, only: String? = null): Boolean {
        if (foldNick(nick) == foldNick(ownNick())) return fail(nick, "own-nick", listOf(tag), res)
        val plaintext = JsonObject(inner.filterKeys { it != "lv" && it != "fb" } + ("lv" to JsonPrimitive(own.list.ver))).toString()
        val prio = when (inner["t"]?.jsonPrimitive?.contentOrNull) {
            "chal", "ack" -> "control"; "list", "listreq" -> "reactive"; else -> "text"
        }
        val msg = Outgoing(state.nextRef++.toString(), plaintext, prio, 1, tag)
        val master = state.pins[foldNick(nick)]
        return if (master == null) enqueue(nick, "", only, emptyList(), msg, res) else sendTo(nick, master, only, msg, res)
    }

    private fun fail(nick: String, reason: String, tags: List<String>, res: Result): Boolean {
        res.events += E2eEvent.SendFailed(nick, reason, tags)
        return false
    }

    private fun lost(nick: String, reason: String, msgs: List<Outgoing>, res: Result) {
        if (msgs.isNotEmpty()) fail(nick, reason, msgs.map { it.tag }, res)
    }

    private fun working(d: PeerDevice) = d.ed25519 !in state.rekeying && sessionsOf(d.curve25519).isNotEmpty()

    private fun listHeld(master: String) = (state.masters[master]?.ver ?: 0) > 0

    private fun trustedDevices(master: String): List<PeerDevice> =
        if (state.masters[master] == null) emptyList()
        else state.devices.values.filter { it.master == master && standing(master, it.ed25519, it.certVer) == null }

    private fun split(master: String, only: String?, done: List<String>): Pair<List<PeerDevice>, List<String>> {
        fun due(k: String) = (only == null || k == only) && k !in done
        val trusted = trustedDevices(master)
        val ready = trusted.filter { due(it.ed25519) && working(it) }
        val wanted = (state.masters[master]?.devices.orEmpty() + trusted.map { it.ed25519 }).toSet()
        return ready to wanted.filter { due(it) && ready.none { d -> d.ed25519 == it } }
    }

    private fun sendTo(nick: String, master: String, only: String?, msg: Outgoing, res: Result): Boolean {
        val (ready, lacking) = split(master, only, emptyList())
        if (ready.isNotEmpty()) encrypt(nick, master, ready, msg, res)
        val reached = ready.map { it.ed25519 }
        if (lacking.isNotEmpty() || (!listHeld(master) && (only == null || ready.isEmpty()))) {
            return enqueue(nick, master, only, reached, msg, res) || ready.isNotEmpty()
        }
        if (ready.isNotEmpty()) return true
        lost(nick, "unpinned-key", listOf(msg), res) // `only` names a device this master does not vouch for
        return false
    }

    private fun liveQueue(nick: String, res: Result): List<Queued> {
        val q = state.outq[foldNick(nick)].orEmpty()
        val live = q.filter { it.at > now() - OUTQ_TTL_MS }
        q.filter { it !in live }.forEach { lost(it.nick, "keys-timeout", listOf(it.msg), res) }
        if (live.isNotEmpty()) state.outq[foldNick(nick)] = live else state.outq.remove(foldNick(nick))
        return live
    }

    private fun enqueue(nick: String, master: String, only: String?, done: List<String>, msg: Outgoing, res: Result): Boolean {
        val q = liveQueue(nick, res)
        if (q.size >= OUTQ_MAX) { lost(nick, "queue-full", listOf(msg), res); return false }
        state.outq[foldNick(nick)] = q + Queued(nick, master, only, done, msg, now())
        if (msg.tag != LIST_TAG) res.events += E2eEvent.SendQueued(nick, q.size + 1, msg.tag)
        maybeKeyreq(nick, res)
        return true
    }

    private fun maybeKeyreq(nick: String, res: Result) {
        val key = foldNick(nick)
        val last = state.keyreqSentAt[key]
        if (last != null && now() - last < KEYREQ_RETRY_MS) return
        state.keyreqSentAt[key] = now()
        state.keysAwaited += key
        res.outbound += Outbound(nick, "keyreq", own.did, "*", """{"v":$WIRE_VERSION}""", "control")
    }

    private class Body(val device: PeerDevice, val session: Session, val preKey: Boolean, val body: String)

    private fun encryptOn(d: PeerDevice, plaintext: String): Body {
        val newest = sessionsOf(d.curve25519).firstOrNull() ?: error("no session to encrypt on for ${d.ed25519}")
        val session = Session.fromPickle(newest)
        val preKey = !session.hasReceivedMessage()
        val inner = if (preKey) {
            JsonObject(Json.parseToJsonElement(plaintext).jsonObject + ("fb" to JsonPrimitive(fbOf(account.ed25519Key(), d.ed25519)))).toString()
        } else plaintext
        val enc = session.encrypt(inner.toByteArray())
        check((enc.messageType == 0uL) == preKey) { "a session wrote another kind of message" }
        return Body(d, session, preKey, b64(enc.body))
    }

    private fun encrypt(nick: String, master: String, devices: List<PeerDevice>, msg: Outgoing, res: Result) {
        val bodies = devices.map { encryptOn(it, msg.plaintext) }
        try {
            // The MSG first (rev 2: never both kinds in one message); at most MAX_DEVICES bodies each.
            for (preKey in listOf(false, true)) {
                for (group in bodies.filter { it.preKey == preKey }.chunked(MAX_DEVICES)) {
                    for (b in group) state.sessions[b.device.curve25519] = listOf(b.session.pickle()) + sessionsOf(b.device.curve25519).drop(1)
                    val ref = (state.nextRef++).toString()
                    state.sent += Sent(ref, null, nick, master, group.associate { it.device.did to SentDevice(it.device.ed25519, it.session.sessionId()) }, msg, now())
                    res.outbound += Outbound(nick, if (preKey) "pkmsg" else "msg", own.did, if (group.size == 1) group[0].device.did else "*", wire(preKey, group.associate { it.device.did to it.body }), msg.priority, ref, msg.tag)
                }
            }
        } finally {
            bodies.forEach { it.session.close() }
        }
    }

    private fun wire(preKey: Boolean, bodies: Map<String, String>): String = buildJsonObject {
        put("v", WIRE_VERSION)
        if (preKey) {
            put("id", buildJsonObject { val id = idBlock(account, own.master); put("curve25519", id.curve25519); put("ed25519", id.ed25519); put("sig", id.sig) })
            put("master", buildJsonObject { put("ed25519", own.master); put("cert", own.cert); put("ver", own.certVer) })
        } else put("ck", account.curve25519Key())
        put("bodies", buildJsonObject { bodies.forEach { (k, v) -> put(k, v) } })
    }.toString()

    /** grappa named the message: a NACK refers to it by this msgid. */
    fun sent(ref: String, msgid: String) {
        val i = state.sent.indexOfFirst { it.ref == ref }
        if (i >= 0) state.sent[i] = state.sent[i].copy(msgid = msgid)
    }

    // ── receiving ──

    /** One whole message from a peer (`own: false`): [verb] keys|pkmsg|msg|nack, [payload] the JSON text. */
    fun receive(nick: String, verb: String, fromDid: String, msgid: String, payload: String): Result {
        val res = Result()
        try {
            val p = Json.parseToJsonElement(payload) as? JsonObject ?: throw PayloadError("payload is not a JSON object")
            when (verb) {
                "keys" -> onKeys(nick, p, res)
                "pkmsg" -> onPkmsg(nick, msgid, fromDid, p, res)
                "msg" -> onMsg(nick, msgid, fromDid, p, res)
                "nack" -> onNack(nick, fromDid, p, res)
            }
        } catch (e: Exception) {
            if (e !is PayloadError && e !is IllegalArgumentException && e !is kotlinx.serialization.SerializationException) throw e
            res.events += E2eEvent.Dropped(nick, "undecodable-payload", verb)
        }
        return res
    }

    private fun drop(nick: String, reason: String, verb: String, res: Result) {
        res.events += E2eEvent.Dropped(nick, reason, verb)
    }

    private fun checkMaster(nick: String, master: String, res: Result): Boolean {
        if (master == own.master) { drop(nick, "own-identity", "keys", res); return false }
        val pinned = state.pins[foldNick(nick)]
        if (pinned == master) return true
        if (pinned != null) { res.events += E2eEvent.KeyAlarm(nick, pinned, master); return false }
        state.masters.getOrPut(master) { PeerMaster(nick) }
        state.pins[foldNick(nick)] = master
        return true
    }

    private fun validMasterList(master: String, ver: Int, devices: List<String>, sig: String): Boolean {
        if (devices.size !in 1..MAX_DEVICES || ver !in 1..Int.MAX_VALUE) return false
        if (devices.zipWithNext().any { (a, b) -> a >= b }) return false
        if (!ed(master) || devices.any { !ed(it) }) return false
        if (devices.map { didOf(it) }.toSet().size != devices.size) return false
        return verifyEd25519(master, listSigText(ver, devices), sig)
    }

    private fun onKeys(nick: String, p: JsonObject, res: Result) {
        val key = foldNick(nick)
        if (key !in state.keysAwaited) return drop(nick, "unsolicited-keys", "keys", res)
        if (p["v"]?.jsonPrimitive?.intOrNull != WIRE_VERSION) throw PayloadError("unsupported version")
        val m = p.obj("master")
        val list = m.obj("list")
        val ver = list["ver"]?.jsonPrimitive?.intOrNull ?: throw PayloadError("missing ver")
        val devs = (list["devices"] as? JsonArray ?: throw PayloadError("missing devices")).map { it.jsonPrimitive.content }
        val master = m.str("ed25519")
        if (!validMasterList(master, ver, devs, m.str("sig"))) return drop(nick, "bad-list", "keys", res)
        val entries = (p["devices"] as? JsonArray ?: throw PayloadError("missing devices")).also { if (it.size > MAX_DEVICES) throw PayloadError("missing devices") }
        state.keysAwaited -= key
        if (!checkMaster(nick, master, res)) {
            val q = liveQueue(nick, res)
            state.outq.remove(key)
            lost(nick, "key-mismatch", q.map { it.msg }, res)
            return
        }
        applyList(master, ver, devs)
        val judged = mutableSetOf<String>()
        for (e in entries) {
            val o = e.jsonObject
            val id = idOf(o.obj("id"))
            if (!judged.add(id.ed25519)) continue
            val otk = o.obj("otk")
            openSession(nick, master, id, otk.str("key"), otk.str("sig"), res)
        }
        flush(nick, master, res)
    }

    private fun idOf(o: JsonObject) = IdBlock(o.str("curve25519"), o.str("ed25519"), o.str("sig"))

    private fun idSigned(id: IdBlock, master: String) = verifyEd25519(id.ed25519, idSigText(id.curve25519, master), id.sig)

    private fun canonical(id: IdBlock) = ed(id.ed25519) && curveOk(id.curve25519)

    private fun standing(master: String, ed25519: String, certVer: Int?): String? {
        val r = state.masters[master] ?: error("a device presented under a master no pin names")
        if (ed25519 in r.devices || (certVer != null && certVer > r.ver && ed25519 !in r.revoked)) return null
        if (ed25519 in r.revoked) return "revoked-device"
        return if (certVer == null) "unlisted-device" else "stale-certificate"
    }

    private fun curveRefusal(id: IdBlock, proving: Boolean): String? {
        if (!canonical(id)) return "non-canonical-key"
        if (id.curve25519 == account.curve25519Key()) return "own-identity"
        val known = state.devices[id.ed25519]
        if (known != null && known.curve25519 != id.curve25519) return "identity-curve-mismatch"
        val owner = state.devices.values.firstOrNull { it.curve25519 == id.curve25519 }
        if (owner != null && owner.ed25519 != id.ed25519 && (owner.proven || !proving)) return "curve-already-bound"
        return null
    }

    private fun applyList(master: String, ver: Int, devices: List<String>) {
        val r = state.masters[master] ?: error("a list for a master no pin names")
        if (ver <= r.ver) return
        val leaving = r.devices.filter { it !in devices }
        val back = r.revoked.filter { it in devices }
        fun curves(keys: List<String>) = keys.mapNotNull { k -> state.devices[k]?.takeIf { it.master == master }?.curve25519 }
        val upd = r.copy(
            revoked = r.revoked.filter { it !in back } + leaving,
            revokedCurves = r.revokedCurves.filter { it !in curves(back) } + curves(leaving),
            ver = ver,
            devices = devices,
        )
        state.masters[master] = upd
        for (d in state.devices.values.toList()) {
            if (d.master == master && standing(master, d.ed25519, d.certVer) != null) {
                state.sessions.remove(d.curve25519); state.rekeying -= d.ed25519
            }
        }
    }

    private fun openSession(nick: String, master: String, id: IdBlock, otk: String, otkSig: String, res: Result): Boolean {
        val reason = (if (!idSigned(id, master)) "bad-identity-signature" else standing(master, id.ed25519, null))
            ?: curveRefusal(id, false)
            ?: if (verifyEd25519(id.ed25519, "$OTK_SIG_PREFIX$otk", otkSig)) null else "bad-otk-signature"
        if (reason != null) { drop(nick, reason, "keys", res); return false }
        if (id.ed25519 !in state.rekeying && sessionsOf(id.curve25519).isNotEmpty()) return false
        val session = try {
            account.createOutboundSession(id.curve25519, otk)
        } catch (e: uniffi.vodozemac_android.CryptoException) {
            drop(nick, "bad-payload", "keys", res); return false
        }
        session.use { addSession(id.curve25519, it.pickle()) }
        state.rekeying -= id.ed25519
        val known = state.devices[id.ed25519]
        state.devices[id.ed25519] = PeerDevice(id.ed25519, id.curve25519, master, didOf(id.ed25519), known?.proven ?: false, known?.certVer)
        return true
    }

    private fun flush(nick: String, master: String, res: Result) {
        val queue = liveQueue(nick, res)
        state.outq.remove(foldNick(nick))
        val waiting = mutableListOf<Queued>()
        for (q in queue) {
            if (q.master != master && q.master != "") { lost(nick, "key-mismatch", listOf(q.msg), res); continue }
            val (ready, lacking) = split(master, q.only, q.done)
            if (ready.isNotEmpty()) encrypt(nick, master, ready, q.msg, res)
            val done = q.done + ready.map { it.ed25519 }
            if (lacking.isNotEmpty()) waiting += q.copy(master = master, done = done)
            // Gone to every device it was for is no loss; only a message that reached none was for
            // a device this master's list does not name (cicchetto judges this at the end of the step).
            else if (done.isEmpty()) lost(nick, "unpinned-key", listOf(q.msg), res)
        }
        if (waiting.isNotEmpty()) state.outq[foldNick(nick)] = waiting
    }

    private fun sessionsOf(curve: String) = state.sessions[curve].orEmpty()

    private fun addSession(curve: String, pickle: String) {
        state.sessions[curve] = (listOf(pickle) + sessionsOf(curve)).take(MAX_SESSIONS_PER_PEER)
    }

    private fun sessionIdOf(pickle: String) = Session.fromPickle(pickle).use { it.sessionId() }

    private fun undecryptable(nick: String, msgid: String, fromDid: String, reason: String, res: Result) {
        res.events += E2eEvent.Undecryptable(nick, msgid, reason)
        res.outbound += Outbound(nick, "nack", own.did, fromDid, """{"v":$WIRE_VERSION,"ref":${JsonPrimitive(msgid)}}""", "control")
    }

    private fun bodyFor(bodies: JsonObject): ByteArray? =
        (bodies[own.did] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { b64d.decode(it) }

    private fun bodiesOf(p: JsonObject): JsonObject {
        val b = p.obj("bodies")
        if (b.size !in 1..MAX_DEVICES) throw PayloadError("bad bodies")
        return b
    }

    private fun onPkmsg(nick: String, msgid: String, fromDid: String, p: JsonObject, res: Result) {
        if (p["v"]?.jsonPrimitive?.intOrNull != WIRE_VERSION) throw PayloadError("unsupported version")
        val id = idOf(p.obj("id"))
        val mb = p.obj("master")
        val master = mb.str("ed25519")
        val certVer = mb["ver"]?.jsonPrimitive?.intOrNull?.takeIf { it in 1..Int.MAX_VALUE } ?: throw PayloadError("bad ver")
        val cert = mb.str("cert")
        val bodies = bodiesOf(p)
        if (!canonical(id) || !ed(master)) return drop(nick, "non-canonical-key", "pkmsg", res)
        if (didOf(id.ed25519) != fromDid) return drop(nick, "bad-from", "pkmsg", res)
        val body = bodyFor(bodies) ?: return undecryptable(nick, msgid, fromDid, "not-for-our-devices", res)
        if (!verifyEd25519(master, certSigText(certVer, id.ed25519), cert)) return drop(nick, "bad-cert", "pkmsg", res)
        if (!idSigned(id, master)) return drop(nick, "bad-identity-signature", "pkmsg", res)
        if (!checkMaster(nick, master, res)) return
        (standing(master, id.ed25519, certVer) ?: curveRefusal(id, true))?.let { return drop(nick, it, "pkmsg", res) }
        val sessionId = try { preKeySessionId(body) } catch (e: uniffi.vodozemac_android.CryptoException) { return drop(nick, "bad-payload", "pkmsg", res) }
        val bound = state.devices[id.ed25519]
        val held = if (bound == null) null else sessionsOf(id.curve25519).firstOrNull { sessionIdOf(it) == sessionId }
        if (bound != null && held != null) {
            val plaintext = decryptWith(id.curve25519, held, 0uL, body)
                ?: return undecryptable(nick, msgid, fromDid, "decryption-failed", res)
            inner(nick, plaintext, id.ed25519, res)?.let { deliver(nick, master, bound, it, bodies.keys, res) }
            return
        }
        if (sessionId in state.seen) return undecryptable(nick, msgid, fromDid, "replayed-session", res)
        val created = try { account.createInboundSession(id.curve25519, body) } catch (e: uniffi.vodozemac_android.CryptoException) {
            return undecryptable(nick, msgid, fromDid, "session-creation", res)
        }
        state.seen += sessionId
        if (state.seen.size > SEEN_SESSIONS_MAX) state.seen.subList(0, state.seen.size - SEEN_SESSIONS_MAX).clear()
        created.session.use { s ->
            val inner = inner(nick, created.plaintext, id.ed25519, res) ?: return // built for another pair, or unreadable: the session goes
            val device = bindProven(master, id, certVer)
            addSession(id.curve25519, s.pickle())
            deliver(nick, master, device, inner, bodies.keys, res)
        }
        flush(nick, master, res)
    }

    private fun onMsg(nick: String, msgid: String, fromDid: String, p: JsonObject, res: Result) {
        if (p["v"]?.jsonPrimitive?.intOrNull != WIRE_VERSION) throw PayloadError("unsupported version")
        val curve = runCatching { canonicalCurve25519(p.str("ck")) }.getOrElse { throw PayloadError("bad ck") }
        val bodies = bodiesOf(p)
        val body = bodyFor(bodies) ?: return undecryptable(nick, msgid, fromDid, "not-for-our-devices", res)
        val master = state.pins[foldNick(nick)]
        val record = master?.let { state.masters[it] }
        if (record != null && curve in record.revokedCurves) return drop(nick, "revoked-device", "msg", res)
        val device = master?.let { m -> trustedDevices(m).firstOrNull { it.curve25519 == curve } }
        if (master != null && device != null) {
            if (device.did != fromDid) return drop(nick, "bad-from", "msg", res)
            // Nothing about the device moves before a decrypt succeeds: `ck` is public.
            val plaintext = sessionsOf(curve).toList().firstNotNullOfOrNull { decryptWith(curve, it, 1uL, body) }
            if (plaintext == null) {
                return undecryptable(nick, msgid, fromDid, if (sessionsOf(curve).isEmpty()) "no-session" else "decryption-failed", res)
            }
            inner(nick, plaintext, null, res)?.let { deliver(nick, master, device, it, bodies.keys, res) }
            return
        }
        if (state.devices.values.any { it.curve25519 == curve }) return drop(nick, "unpinned-key", "msg", res)
        undecryptable(nick, msgid, fromDid, "no-session", res)
    }

    private fun bindProven(master: String, id: IdBlock, certVer: Int): PeerDevice {
        // Unbind a device of another key claiming this curve, with the sessions opened on its claim.
        state.devices.values.filter { it.curve25519 == id.curve25519 && it.ed25519 != id.ed25519 }.forEach {
            state.devices.remove(it.ed25519); state.rekeying -= it.ed25519
        }
        return PeerDevice(id.ed25519, id.curve25519, master, didOf(id.ed25519), proven = true, certVer = certVer)
            .also { state.devices[id.ed25519] = it }
    }

    /** Decrypts on [pickle]; on success it becomes the newest session to [curve]. Null when it cannot, and nothing moves. */
    private fun decryptWith(curve: String, pickle: String, type: ULong, body: ByteArray): ByteArray? {
        val session = Session.fromPickle(pickle)
        return try {
            val plain = session.decrypt(type, body)
            state.sessions[curve] = listOf(session.pickle()) + sessionsOf(curve).filter { it != pickle }
            plain
        } catch (e: uniffi.vodozemac_android.CryptoException) {
            null
        } finally {
            session.close()
        }
    }

    private fun inner(nick: String, plaintext: ByteArray, sender: String?, res: Result): JsonObject? {
        val o = runCatching { Json.parseToJsonElement(String(plaintext, Charsets.UTF_8)) as? JsonObject }.getOrNull()
        if (o == null) { res.events += E2eEvent.Dropped(nick, "undecodable-inner", null); return null }
        if ((o["t"] as? JsonPrimitive)?.isString != true) { res.events += E2eEvent.Dropped(nick, "inner-without-type", null); return null }
        if (sender != null && (o["fb"] as? JsonPrimitive)?.contentOrNull != fbOf(sender, account.ed25519Key())) {
            res.events += E2eEvent.Dropped(nick, "inner-key-mismatch", null); return null
        }
        return JsonObject(o.filterKeys { it != "fb" })
    }

    // A decrypted inner message from [device] of [master] (the master pinned to [nick]), out of a message
    // holding bodies for [bodies] (dids). t:list and t:listreq are the engine's own and never handed on;
    // anything else is, without its `lv`. A body for a device our own list does not name means the writer
    // holds an older list of ours, so it gets the current one; an `lv` above the version of the list we
    // hold for the master asks that device for its list.
    private fun deliver(nick: String, master: String, device: PeerDevice, inner: JsonObject, bodies: Set<String>, res: Result) {
        val lv = (inner["lv"] as? JsonPrimitive)?.intOrNull
        val rest = JsonObject(inner.filterKeys { it != "lv" })
        when (rest["t"]?.jsonPrimitive?.contentOrNull) {
            "list" -> onList(nick, rest, res)
            "listreq" -> if (!sendList(nick, device, res)) res.events += E2eEvent.Dropped(nick, "listreq-rate-limited", null)
            else -> res.events += E2eEvent.Received(nick, master, device.ed25519, state.masters[master]?.verified == true, rest)
        }
        val ours = own.list.devices.map { didOf(it) }.toSet()
        if (bodies.any { it !in ours }) sendList(nick, device, res)
        if (lv != null && lv in 1..Int.MAX_VALUE && lv > (state.masters[master]?.ver ?: 0)) requestList(nick, device.ed25519, lv, res)
    }

    private fun requestList(nick: String, only: String?, lv: Int?, res: Result): Boolean {
        val key = foldNick(nick)
        val master = state.pins[key]
        val ver = master?.let { state.masters[it]?.ver } ?: 0
        val last = state.listreqAt[key]
        val asked = last?.lv.orEmpty().filter { it > ver }
        if (last != null) state.listreqAt[key] = last.copy(lv = asked)
        if (lv != null && lv in asked) return false
        if (last != null && now() - last.at < LISTREQ_INTERVAL_MS) return false
        if (!dispatch(nick, buildJsonObject { put("t", "listreq") }, LIST_TAG, res, only)) return false
        state.listreqAt[key] = ListReq(now(), if (lv == null) asked else asked + lv)
        return true
    }

    private fun sendList(nick: String, device: PeerDevice, res: Result): Boolean {
        val t = now()
        state.listAnsweredAt.entries.removeAll { t - it.value >= LISTREQ_INTERVAL_MS }
        val asker = "${foldNick(nick)} ${device.did}"
        if (asker in state.listAnsweredAt) return false
        state.listAnsweredAt[asker] = t
        val master = buildJsonObject {
            put("ed25519", own.master)
            put("list", buildJsonObject { put("ver", own.list.ver); put("devices", JsonArray(own.list.devices.map(::JsonPrimitive))) })
            put("sig", own.list.sig)
        }
        dispatch(nick, buildJsonObject { put("t", "list"); put("master", master) }, LIST_TAG, res, device.ed25519)
        return true
    }

    // A `t:list`: the KEYS steps 3-5. An invalid list is dropped; one of another master than the one pinned to
    // [nick] is the alarm; else rule 3 (applyList).
    private fun onList(nick: String, inner: JsonObject, res: Result) {
        val m = inner["master"] as? JsonObject
        val l = m?.get("list") as? JsonObject
        val ver = (l?.get("ver") as? JsonPrimitive)?.intOrNull
        val devs = (l?.get("devices") as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: "" }
        val key = (m?.get("ed25519") as? JsonPrimitive)?.takeIf { it.isString }?.content
        val sig = (m?.get("sig") as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (ver == null || devs == null || key == null || sig == null || !validMasterList(key, ver, devs, sig)) {
            res.events += E2eEvent.Dropped(nick, "bad-list", null); return
        }
        if (!checkMaster(nick, key, res)) return
        applyList(key, ver, devs)
    }

    private fun onNack(nick: String, fromDid: String, p: JsonObject, res: Result) {
        if (p["v"]?.jsonPrimitive?.intOrNull != WIRE_VERSION) throw PayloadError("unsupported version")
        val ref = p.str("ref")
        val sent = state.sent.firstOrNull { it.msgid == ref } ?: return drop(nick, "unknown-nack-ref", "nack", res)
        val body = sent.devices[fromDid] ?: return drop(nick, "unknown-nack-ref", "nack", res)
        val rest = sent.devices - fromDid
        state.sent.remove(sent)
        if (rest.isNotEmpty()) state.sent += sent.copy(devices = rest)
        if (foldNick(sent.nick) != foldNick(nick)) return lost(sent.nick, "nack-from-another-nick", listOf(sent.msg), res)
        if (state.pins[foldNick(nick)] != sent.master) return lost(nick, "repinned", listOf(sent.msg), res)
        val device = state.devices[body.ed25519]
        if (device?.master != sent.master || standing(sent.master, device.ed25519, device.certVer) != null) {
            drop(nick, "revoked-device", "nack", res)
            return lost(nick, "unpinned-key", listOf(sent.msg), res)
        }
        if (sent.msg.sends >= MAX_SENDS) return lost(nick, "resend-failed", listOf(sent.msg), res)
        val newest = sessionsOf(device.curve25519).firstOrNull()
        if (newest == null || sessionIdOf(newest) == body.sessionId) {
            if (foldNick(nick) !in state.keysAwaited) {
                val last = state.rekeyAt[foldNick(nick)]
                if (last != null && now() - last < REKEY_INTERVAL_MS) return lost(nick, "rekey-throttled", listOf(sent.msg), res)
                state.rekeyAt[foldNick(nick)] = now()
                res.events += E2eEvent.Rekeying(nick, device.ed25519)
            }
            state.rekeying += device.ed25519
        }
        state.sessions[device.curve25519] = sessionsOf(device.curve25519).filter { sessionIdOf(it) != body.sessionId }
        if (state.sessions[device.curve25519].isNullOrEmpty()) state.sessions.remove(device.curve25519)
        sendTo(nick, sent.master, device.ed25519, sent.msg.copy(sends = sent.msg.sends + 1), res)
    }

    /** Fetches [nick]'s keys (a KEYREQ, under the retry budget): what follows an accepted request. */
    fun askKeys(nick: String): Result {
        val res = Result()
        if (foldNick(nick) != foldNick(ownNick())) maybeKeyreq(nick, res)
        return res
    }

    /** The user dropped the conversation's queue: nick gone. Mirrors `absent`. */
    fun absent(nick: String): Result {
        val res = Result()
        val k = foldNick(nick)
        state.outq.remove(k)?.forEach { lost(it.nick, "no-such-nick", listOf(it.msg), res) }
        state.keysAwaited -= k
        state.keyreqSentAt.remove(k)
        return res
    }
}
