package pm.antani.resentin.e2e

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import uniffi.vodozemac_android.Account
import uniffi.vodozemac_android.canonicalCurve25519
import uniffi.vodozemac_android.canonicalEd25519
import uniffi.vodozemac_android.verifyEd25519

// The five plaintexts two devices of one account exchange over the pairing channel (CLIENT_PROTOCOL
// §4g, cicchetto `pairingMessages.ts`). Compact JSON, "v":1, a "t" kind. An unknown t, a v other
// than 1 or an ill-typed field ends the pairing as failed (decode returns null). Public keys and
// signatures only: nothing secret crosses.

const val PAIRING_INFO = "irc-e2e:v2:pair"

/** Why a network was not paired. */
object PairRefusal {
    const val FULL = "full"
    const val PAIRED = "paired"
    const val LIST_CHANGED = "list-changed"
    const val FAILED = "failed"
    val all = setOf(FULL, PAIRED, LIST_CHANGED, FAILED)
}

data class Refused(val network: String, val reason: String)
data class SignedDevice(val network: String, val cert: String, val list: SignedList)

sealed interface PairMsg {
    data object Hello : PairMsg
    data class Offer(val networks: List<Pair<String, String>>, val full: List<String>) : PairMsg
    data class Join(val devices: List<Pair<String, IdBlock>>, val skipped: List<Refused>) : PairMsg
    data class Signed(val devices: List<SignedDevice>, val refused: List<Refused>) : PairMsg
    data class Done(val paired: List<String>) : PairMsg
}

private fun strs(xs: List<String>) = JsonArray(xs.map(::JsonPrimitive))
private fun refused(xs: List<Refused>) = JsonArray(xs.map { buildJsonObject { put("network", it.network); put("reason", it.reason) } })
private fun idJson(id: IdBlock) = buildJsonObject { put("curve25519", id.curve25519); put("ed25519", id.ed25519); put("sig", id.sig) }

fun encodePairMessage(m: PairMsg): ByteArray = buildJsonObject {
    put("v", 1)
    when (m) {
        PairMsg.Hello -> put("t", "hello")
        is PairMsg.Offer -> {
            put("t", "offer")
            put("networks", JsonArray(m.networks.map { (n, master) -> buildJsonObject { put("network", n); put("master", master) } }))
            put("full", strs(m.full))
        }
        is PairMsg.Join -> {
            put("t", "join")
            put("devices", JsonArray(m.devices.map { (n, id) -> buildJsonObject { put("network", n); put("id", idJson(id)) } }))
            put("skipped", refused(m.skipped))
        }
        is PairMsg.Signed -> {
            put("t", "signed")
            put("devices", JsonArray(m.devices.map { d ->
                buildJsonObject {
                    put("network", d.network); put("cert", d.cert)
                    put("list", buildJsonObject { put("ver", d.list.ver); put("devices", strs(d.list.devices)); put("sig", d.list.sig) })
                }
            }))
            put("refused", refused(m.refused))
        }
        is PairMsg.Done -> { put("t", "done"); put("paired", strs(m.paired)) }
    }
}.toString().toByteArray()

private fun JsonElement?.obj() = this as? JsonObject
private fun JsonElement?.str() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
private fun <T> JsonElement?.list(read: (JsonElement) -> T?): List<T>? =
    (this as? JsonArray)?.map { read(it) ?: return null }

private fun ed(k: JsonElement?) = k.str()?.takeIf { runCatching { canonicalEd25519(it) }.getOrNull() == it }
private fun curve(k: JsonElement?) = k.str()?.takeIf { runCatching { canonicalCurve25519(it) }.getOrNull() == it }
private fun slug(v: JsonElement?) = v.str()?.takeIf { it.isNotEmpty() }
private fun id(v: JsonElement?): IdBlock? {
    val o = v.obj() ?: return null
    return IdBlock(curve(o["curve25519"]) ?: return null, ed(o["ed25519"]) ?: return null, o["sig"].str() ?: return null)
}
private fun refusedOf(v: JsonElement): Refused? {
    val o = v.obj() ?: return null
    val why = o["reason"].str()?.takeIf { it in PairRefusal.all } ?: return null
    return Refused(slug(o["network"]) ?: return null, why)
}
private fun signedList(v: JsonElement?): SignedList? {
    val o = v.obj() ?: return null
    val ver = (o["ver"] as? JsonPrimitive)?.intOrNull ?: return null
    return SignedList(ver, o["devices"].list { ed(it) } ?: return null, o["sig"].str() ?: return null)
}

fun decodePairMessage(bytes: ByteArray): PairMsg? {
    val o = runCatching { Json.parseToJsonElement(String(bytes, Charsets.UTF_8)) }.getOrNull().obj() ?: return null
    if ((o["v"] as? JsonPrimitive)?.intOrNull != 1) return null
    return when (o["t"].str()) {
        "hello" -> PairMsg.Hello
        "offer" -> PairMsg.Offer(
            o["networks"].list { n -> n.obj()?.let { slug(it["network"]) to ed(it["master"]) }?.takeIf { it.first != null && it.second != null }?.let { it.first!! to it.second!! } } ?: return null,
            o["full"].list { slug(it) } ?: return null,
        )
        "join" -> PairMsg.Join(
            o["devices"].list { d -> d.obj()?.let { slug(it["network"]) to id(it["id"]) }?.takeIf { it.first != null && it.second != null }?.let { it.first!! to it.second!! } } ?: return null,
            o["skipped"].list(::refusedOf) ?: return null,
        )
        "signed" -> PairMsg.Signed(
            o["devices"].list { d ->
                val r = d.obj() ?: return@list null
                SignedDevice(slug(r["network"]) ?: return@list null, r["cert"].str() ?: return@list null, signedList(r["list"]) ?: return@list null)
            } ?: return null,
            o["refused"].list(::refusedOf) ?: return null,
        )
        "done" -> PairMsg.Done(o["paired"].list { slug(it) } ?: return null)
        else -> null
    }
}

private val b64url = Base64.getUrlEncoder().withoutPadding()
private val b64urlDec = Base64.getUrlDecoder()

/** The joiner's first payload carries a `|` the relay refuses, so it travels as base64url of its UTF-8. */
fun wrapInitial(message: String): String = b64url.encodeToString(message.toByteArray())

fun unwrapInitial(payload: String): String? =
    if (!Regex("^[A-Za-z0-9_-]+$").matches(payload)) null else runCatching { String(b64urlDec.decode(payload), Charsets.UTF_8) }.getOrNull()

/** The list and certificate [master] signs to add [joinerId]: (result, refusal), exactly one non-null. The joiner must vouch for this master. */
fun issueDeviceOrRefusal(master: Account, own: OwnIdentity, joinerId: IdBlock): Pair<Pair<SignedList, String>?, String?> {
    val (c, e, sig) = joinerId
    if (runCatching { canonicalCurve25519(c) }.getOrNull() != c || runCatching { canonicalEd25519(e) }.getOrNull() != e) return null to PairRefusal.FAILED
    if (!verifyEd25519(e, idSigText(c, own.master), sig)) return null to PairRefusal.FAILED
    if (e in own.list.devices) return null to PairRefusal.PAIRED
    if (own.list.devices.size >= DEVICE_CAP) return null to PairRefusal.FULL
    val ver = own.list.ver + 1
    val devices = (own.list.devices + e).sorted()
    return (SignedList(ver, devices, master.sign(listSigText(ver, devices))) to master.sign(certSigText(ver, e))) to null
}

/** What the joiner keeps of a `signed` entry, once the master's list and certificate both verify and name this device. */
fun acceptSigned(masterKey: String, device: String, cert: String, list: SignedList): Triple<String, Int, SignedList>? {
    if (!validMasterListShape(masterKey, list)) return null
    if (device !in list.devices) return null
    if (!verifyEd25519(masterKey, certSigText(list.ver, device), cert)) return null
    return Triple(cert, list.ver, list)
}

/** The list rules and its signature (shared with KEYS handling): 1..4 sorted canonical keys, distinct dids, master's signature. */
fun validMasterListShape(master: String, list: SignedList): Boolean {
    val devices = list.devices
    if (devices.size !in 1..4 || list.ver !in 1..Int.MAX_VALUE) return false
    if (devices.zipWithNext().any { (a, b) -> a >= b }) return false
    if (runCatching { canonicalEd25519(master) }.getOrNull() != master) return false
    if (devices.any { runCatching { canonicalEd25519(it) }.getOrNull() != it }) return false
    if (devices.map { didOf(it) }.toSet().size != devices.size) return false
    return verifyEd25519(master, listSigText(list.ver, devices), list.sig)
}
