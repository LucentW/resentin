package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

/** One device row of `GET/POST …/e2e/devices` (CLIENT_PROTOCOL §4f). */
@Serializable
data class E2eDeviceDto(
    val did: String,
    val ed25519: String,
    val curve25519: String,
    val sig: String,
    /** v56+, null until the device reads its inbox. */
    val lastSeenAt: String? = null,
    /** v57+. */
    val insertedAt: String? = null,
)

@Serializable
data class E2eDevicesDto(val devices: List<E2eDeviceDto> = emptyList())

@Serializable
data class E2eRegisterDeviceDto(val ed25519: String, val curve25519: String, val sig: String)

/** The master's signed device list: `GET/PUT …/e2e/list`. */
@Serializable
data class E2eListDto(val master: String, val ver: Int, val devices: List<String>, val sig: String)

@Serializable
data class E2eSignedKeyDto(val key: String, val sig: String)

@Serializable
data class E2eKeysUploadDto(val oneTimeKeys: List<E2eSignedKeyDto>, val fallback: E2eSignedKeyDto?)

/** What a device's key bundle still holds on grappa. */
@Serializable
data class E2eKeysStatusDto(val oneTimeKeys: Int = 0, val fallback: Boolean = false)

/** One whole message grappa holds for a device (the `e2e_message` wire shape). `payload` is the peer's compact JSON text. */
@Serializable
data class E2eMessageDto(
    val id: Long,
    val from: String,
    val fromDid: String,
    val toDid: String,
    val verb: String,
    val msgid: String,
    val payload: String,
    /** v50+: a copy of a message one of OUR other devices sent. */
    val own: Boolean = false,
    /** v63+: the folded channel a channel message was sent to; null for a direct message. */
    val channel: String? = null,
)

@Serializable
data class E2eInboxDto(val messages: List<E2eMessageDto> = emptyList())

@Serializable
data class E2eAckDto(val ids: List<Long>)

@Serializable
data class E2eSendDto(
    val target: String,
    val verb: String,
    val from: String,
    val to: String,
    val payload: String,
    val priority: String = "text",
)

@Serializable
data class E2eSentDto(val msgid: String)
