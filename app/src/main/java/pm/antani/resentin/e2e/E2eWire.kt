package pm.antani.resentin.e2e

import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.Serializable

// Wire v2 constants and signature texts, byte-for-byte those of grappa-irc's
// cicchetto/src/lib/e2e/wire.ts (CLIENT_PROTOCOL §4f). A device that signs a
// different string is simply never trusted by its peers, so do not reword these.

const val ID_SIG_PREFIX = "irc-e2e:v2:id:"
const val OTK_SIG_PREFIX = "irc-e2e:v1:otk:"
const val LIST_SIG_PREFIX = "irc-e2e:v2:list:"
const val CERT_PREFIX = "irc-e2e:v2:dev:"

/** The most devices one identity holds on grappa (issue 2576); the wire itself reads up to 4. */
const val DEVICE_CAP = 3

@Serializable
data class IdBlock(val curve25519: String, val ed25519: String, val sig: String)

@Serializable
data class SignedList(val ver: Int, val devices: List<String>, val sig: String)

fun idSigText(curve25519: String, master: String) = "$ID_SIG_PREFIX$curve25519:$master"

fun listSigText(ver: Int, devices: List<String>) = "$LIST_SIG_PREFIX$ver:${devices.joinToString(",")}"

fun certSigText(ver: Int, ed25519: String) = "$CERT_PREFIX$ver:$ed25519"

private val b64 = Base64.getDecoder()
private val b64url = Base64.getUrlEncoder().withoutPadding()

/** grappa's device id: first 8 bytes of SHA-256 of the raw Ed25519 key, base64url, unpadded. */
fun didOf(ed25519: String): String {
    val raw = b64.decode(ed25519.trimEnd('='))
    return b64url.encodeToString(MessageDigest.getInstance("SHA-256").digest(raw).copyOf(8))
}
