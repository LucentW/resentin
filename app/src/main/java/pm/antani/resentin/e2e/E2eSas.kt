package pm.antani.resentin.e2e

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import uniffi.vodozemac_android.EstablishedSas
import uniffi.vodozemac_android.Sas

// Short Authentication String verification of a contact's master key (port of cicchetto `sas.ts`; the
// Matrix SAS shape, 7 emoji both users compare out of band). It runs as inner messages `t:sas.*` inside
// the encrypted chat. vodozemac keeps the SAS secret in a type that cannot be pickled, so a verification
// lives only in the process that started it: after a restart it ends as "page-gone".

const val SAS_TIMEOUT_MS = 600_000L
private const val SAS_INFO = "irc-e2e:v1:sas:"
private const val MAC_INFO = "irc-e2e:v1:sas-mac:"
private const val MMAC_INFO = "irc-e2e:v2:sas-mmac:"
const val SAS_TAG = "sas"
const val SAS_RELEASE_TAG = "sas-release"

/** Matrix's emoji table, verbatim (matrix-spec sas-emoji.json): the index vodozemac prints, the emoji, its English name. */
val SAS_EMOJI: List<Pair<String, String>> = listOf(
    "🐶" to "Dog", "🐱" to "Cat", "🦁" to "Lion", "🐎" to "Horse", "🦄" to "Unicorn", "🐷" to "Pig", "🐘" to "Elephant", "🐰" to "Rabbit",
    "🐼" to "Panda", "🐓" to "Rooster", "🐧" to "Penguin", "🐢" to "Turtle", "🐟" to "Fish", "🐙" to "Octopus", "🦋" to "Butterfly", "🌷" to "Flower",
    "🌳" to "Tree", "🌵" to "Cactus", "🍄" to "Mushroom", "🌏" to "Globe", "🌙" to "Moon", "☁️" to "Cloud", "🔥" to "Fire", "🍌" to "Banana",
    "🍎" to "Apple", "🍓" to "Strawberry", "🌽" to "Corn", "🍕" to "Pizza", "🎂" to "Cake", "❤️" to "Heart", "😀" to "Smiley", "🤖" to "Robot",
    "🎩" to "Hat", "👓" to "Glasses", "🔧" to "Spanner", "🎅" to "Santa", "👍" to "Thumbs Up", "☂️" to "Umbrella", "⌛" to "Hourglass", "⏰" to "Clock",
    "🎁" to "Gift", "💡" to "Light Bulb", "📕" to "Book", "✏️" to "Pencil", "📎" to "Paperclip", "✂️" to "Scissors", "🔒" to "Lock", "🔑" to "Key",
    "🔨" to "Hammer", "☎️" to "Telephone", "🏁" to "Flag", "🚂" to "Train", "🚲" to "Bicycle", "✈️" to "Aeroplane", "🚀" to "Rocket", "🏆" to "Trophy",
    "⚽" to "Ball", "🎸" to "Guitar", "🎺" to "Trumpet", "🔔" to "Bell", "⚓" to "Anchor", "🎧" to "Headphones", "📁" to "Folder", "📌" to "Pin",
)

@Serializable
data class SasRecord(
    var tx: String,
    val nick: String,
    var role: String, // "a" started it, "b" was asked
    var phase: String, // started keyed asked accepted compare
    var at: Long,
    var device: String? = null,
    /** The process holding our secret; a record whose page is not this process's can only end. */
    var page: String? = null,
    var ourKey: String? = null,
    var theirKey: String? = null,
    var commitment: String? = null,
    var expected: String? = null,
    var expectedMmac: String? = null,
    var theirMac: String? = null,
    var theirMmac: String? = null,
)

/** What the UI needs of a running verification. [emoji] is the 7 indices, once both keys are in. */
data class SasView(val role: String, val phase: String, val confirmed: Boolean, val here: Boolean, val emoji: List<Int>?)

/** This process: its id and the secrets of the verifications it started. */
object SasHost {
    val page: String = UUID.randomUUID().toString()
    val secrets = ConcurrentHashMap<String, Any>() // our public key -> Sas | EstablishedSas
}

private val TX = Regex("^[A-Za-z0-9+/]{11}$")
private val KEY = Regex("^[A-Za-z0-9+/]{43}$")
private val b64 = Base64.getEncoder().withoutPadding()

private fun commitmentOf(key: String, tx: String) = b64.encodeToString(MessageDigest.getInstance("SHA-256").digest("$key|$tx".toByteArray()))

class E2eVerifier(
    private val engine: E2eEngine,
    private val state: E2eState,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var res = Result()

    init {
        // A secret nobody's record of this page holds is a leftover: free it.
        val held = state.sas.values.filter { it.page == SasHost.page }.mapNotNull { it.ourKey }.toSet()
        SasHost.secrets.keys.toList().filter { it !in held }.forEach { close(SasHost.secrets.remove(it)) }
    }

    fun take(): Result = res.also { res = Result() }

    /** The user's move: start | accept | match | mismatch | cancel. */
    fun step(nick: String, step: String) {
        if (step == "start") return start(nick)
        val (master, rec) = recordOf(nick) ?: return
        when (step) {
            "accept" -> accept(rec)
            "match" -> confirm(master, rec)
            "mismatch" -> end(master, rec, "mismatch")
            "cancel" -> end(master, rec, "user")
        }
    }

    private fun start(nick: String) {
        val peer = engine.pinned(nick) ?: return
        if (state.sas[peer.master] != null) return
        val rec = SasRecord(
            tx = b64.encodeToString(ByteArray(8).also { SecureRandom().nextBytes(it) }),
            nick = nick, role = "a", phase = "started", at = now(), page = SasHost.page, ourKey = newSecret(),
        )
        state.sas[peer.master] = rec
        send(rec, buildJsonObject { put("t", "sas.start"); put("tx", rec.tx) })
        note(nick, "asked", false, null)
    }

    private fun accept(rec: SasRecord) {
        if (rec.phase != "asked") return
        val ourKey = newSecret()
        rec.page = SasHost.page; rec.ourKey = ourKey; rec.phase = "accepted"
        send(rec, buildJsonObject { put("t", "sas.accept"); put("tx", rec.tx); put("commitment", commitmentOf(ourKey, rec.tx)) })
    }

    private fun confirm(master: String, rec: SasRecord) {
        if (rec.phase != "compare" || rec.expected != null || rec.page != SasHost.page) return
        val (sas, peer) = establish(master, rec) ?: return
        val own = engine.curve()
        val ours = "$own|${peer.curve25519}|${rec.tx}"
        val theirs = "${peer.curve25519}|$own|${rec.tx}"
        val mac = sas.calculateMac(engine.deviceKey(), "$MAC_INFO$ours")
        val mmac = sas.calculateMac(engine.fingerprint(), "$MMAC_INFO$ours")
        rec.expected = sas.calculateMac(peer.ed25519, "$MAC_INFO$theirs")
        rec.expectedMmac = sas.calculateMac(master, "$MMAC_INFO$theirs")
        forget(rec)
        send(rec, buildJsonObject { put("t", "sas.mac"); put("tx", rec.tx); put("mac", mac); put("mmac", mmac) })
        settle(master, rec)
    }

    /** A pin moved under a running verification: it is for a key that is no longer the contact's. */
    fun repinned(nick: String) {
        val pinned = engine.pinned(nick)?.master
        for ((master, rec) in state.sas.toMap()) {
            if (master == pinned || foldNick(rec.nick) != foldNick(nick)) continue
            state.sas.remove(master); forget(rec)
            note(rec.nick, "cancelled", false, "key-changed")
        }
    }

    /** One `t:sas.*` inner message from [device] of [master]. Anything malformed or out of turn is ignored or ends it. */
    fun receive(nick: String, master: String, device: String, inner: JsonObject) {
        val t = inner["t"]?.jsonPrimitive?.contentOrNull ?: return
        val tx = (inner["tx"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { TX.matches(it) } ?: return
        fun field(n: String) = (inner[n] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { KEY.matches(it) }
        val rec = state.sas[master]
        if (t == "sas.start") return onStart(nick, master, device, rec, tx)
        if (rec == null || rec.tx != tx) return
        if (rec.device == null) { release(rec, device); rec.device = device } else if (rec.device != device) return
        when (t) {
            "sas.accept" -> if (rec.phase == "started") {
                val c = field("commitment") ?: return
                rec.commitment = c; rec.phase = "keyed"
                send(rec, buildJsonObject { put("t", "sas.key"); put("tx", rec.tx); put("key", rec.ourKey!!) })
                return
            }
            "sas.key" -> {
                val key = field("key") ?: return
                if (rec.phase == "keyed") {
                    if (commitmentOf(key, rec.tx) != rec.commitment) return end(master, rec, "commitment-mismatch")
                    return compare(rec, key)
                }
                if (rec.phase == "accepted") {
                    send(rec, buildJsonObject { put("t", "sas.key"); put("tx", rec.tx); put("key", rec.ourKey!!) })
                    return compare(rec, key)
                }
            }
            "sas.mac" -> if (rec.phase == "compare" && rec.theirMac == null) {
                rec.theirMac = field("mac") ?: return
                rec.theirMmac = field("mmac") ?: return
                return settle(master, rec)
            }
            "sas.cancel" -> {
                state.sas.remove(master); forget(rec)
                return note(rec.nick, "cancelled", true, (inner["reason"] as? JsonPrimitive)?.contentOrNull?.take(64) ?: "other")
            }
            else -> return
        }
        end(master, rec, "unexpected-message")
    }

    private fun onStart(nick: String, master: String, device: String, rec: SasRecord?, tx: String) {
        if (rec?.tx == tx) return
        if (rec != null && rec.phase == "started" && rec.ourKey != null) {
            // Both sides started at once: the lower tx stays the one that goes on.
            if (tx > rec.tx) return
            release(rec, device)
            rec.tx = tx; rec.role = "b"; rec.phase = "accepted"; rec.at = now(); rec.device = device
            send(rec, buildJsonObject { put("t", "sas.accept"); put("tx", tx); put("commitment", commitmentOf(rec.ourKey!!, tx)) })
            return
        }
        if (rec != null) forget(rec)
        state.sas[master] = SasRecord(tx = tx, nick = nick, role = "b", phase = "asked", at = now(), device = device)
        note(nick, "asked", true, null)
    }

    private fun compare(rec: SasRecord, theirKey: String) { rec.theirKey = theirKey; rec.phase = "compare" }

    private fun settle(master: String, rec: SasRecord) {
        val expected = rec.expected ?: return
        val theirMac = rec.theirMac ?: return
        if (theirMac != expected || rec.theirMmac != rec.expectedMmac) return end(master, rec, "mac-mismatch")
        state.sas.remove(master)
        engine.verify(rec.nick, master)
        note(rec.nick, "verified", false, null)
    }

    /** Ends what timed out, and what only a previous run of the app could finish. */
    fun tick() {
        for ((master, rec) in state.sas.toMap()) {
            if (now() >= rec.at + SAS_TIMEOUT_MS) end(master, rec, "timeout")
            else if (rec.page != null && rec.expected == null && rec.page != SasHost.page) end(master, rec, "page-gone")
        }
    }

    fun view(nick: String): SasView? {
        val (master, rec) = recordOf(nick) ?: return null
        val here = rec.page == SasHost.page
        val established = if (here && rec.phase == "compare" && rec.expected == null) establish(master, rec) else null
        if (state.sas[master] == null) return null
        return SasView(rec.role, rec.phase, rec.expected != null, here, established?.let { (sas, peer) -> sas.emojiIndices(info(rec, peer)).map { it.toInt() } })
    }

    private fun recordOf(nick: String): Pair<String, SasRecord>? {
        val master = engine.pinned(nick)?.master ?: return null
        return state.sas[master]?.let { master to it }
    }

    private fun newSecret(): String {
        val sas = Sas()
        val key = sas.publicKey()
        SasHost.secrets[key] = sas
        return key
    }

    private fun establish(master: String, rec: SasRecord): Pair<EstablishedSas, PeerDevice>? {
        val peer = rec.device?.let { engine.trusted(master, it) }
        if (peer == null) { end(master, rec, "bad-key"); return null }
        val key = rec.ourKey ?: return null
        val secret = SasHost.secrets[key]
        if (secret is EstablishedSas) return secret to peer
        val theirKey = rec.theirKey
        if (secret !is Sas || theirKey == null) return null
        SasHost.secrets.remove(key)
        return try {
            val sas = secret.diffieHellman(theirKey)
            SasHost.secrets[key] = sas
            sas to peer
        } catch (e: uniffi.vodozemac_android.CryptoException) {
            end(master, rec, "bad-key"); null
        }
    }

    // The bytes both sides derive the emoji from: ordered as "a" then "b" so they agree.
    private fun info(rec: SasRecord, peer: PeerDevice): String {
        val ours = "${engine.curve()}|${rec.ourKey}"
        val theirs = "${peer.curve25519}|${rec.theirKey}"
        return "$SAS_INFO${if (rec.role == "a") "$ours|$theirs" else "$theirs|$ours"}|${rec.tx}"
    }

    private fun end(master: String, rec: SasRecord, reason: String) {
        state.sas.remove(master); forget(rec)
        send(rec, buildJsonObject { put("t", "sas.cancel"); put("tx", rec.tx); put("reason", reason) })
        note(rec.nick, "cancelled", false, reason)
    }

    private fun forget(rec: SasRecord) {
        val key = rec.ourKey ?: return
        if (rec.page != SasHost.page) return
        close(SasHost.secrets.remove(key))
    }

    private fun close(secret: Any?) { when (secret) { is Sas -> secret.close(); is EstablishedSas -> secret.close() } }

    private fun send(rec: SasRecord, inner: JsonObject) { collect(engine.send(rec.nick, inner, SAS_TAG, rec.device)) }

    // A second device answering the same verification: tell the others of that contact to stand down.
    private fun release(rec: SasRecord, device: String) {
        val inner = buildJsonObject { put("t", "sas.cancel"); put("tx", rec.tx); put("reason", "other-device") }
        collect(engine.sendExcept(rec.nick, inner, SAS_RELEASE_TAG, device))
    }

    private fun collect(r: Result) { res.events += r.events; res.outbound += r.outbound }

    private fun note(nick: String, kind: String, byPeer: Boolean, reason: String?) { res.events += E2eEvent.SasNote(nick, kind, byPeer, reason) }
}
