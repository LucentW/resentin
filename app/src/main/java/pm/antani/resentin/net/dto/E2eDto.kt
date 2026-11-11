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
