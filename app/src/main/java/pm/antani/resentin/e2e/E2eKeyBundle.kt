package pm.antani.resentin.e2e

import pm.antani.resentin.net.dto.E2eKeysStatusDto
import pm.antani.resentin.net.dto.E2eKeysUploadDto
import pm.antani.resentin.net.dto.E2eSignedKeyDto
import uniffi.vodozemac_android.Account

/** One-time keys grappa holds per device (`E2eKeys.max_one_time_keys/0`); an upload past it is a 413. */
const val MAX_HOSTED_ONE_TIME_KEYS = 50

/** The id block of [device] under [master]: its keys and its signature over curve key + master. */
fun idBlock(device: Account, master: String): IdBlock {
    val curve = device.curve25519Key()
    return IdBlock(curve, device.ed25519Key(), device.sign(idSigText(curve, master)))
}

fun signedKey(device: Account, key: String) = E2eSignedKeyDto(key, device.sign("$OTK_SIG_PREFIX$key"))

/**
 * What to upload so grappa, holding [held] for [device], ends with every one-time key it has
 * room for plus a fallback key; null when it already has both. Generates keys on [device],
 * unpublished. Keys an earlier upload never confirmed go first, oldest first, capped at the
 * room left so an upload is never refused for size.
 */
fun topUp(device: Account, held: E2eKeysStatusDto): E2eKeysUploadDto? {
    val room = maxOf(0, MAX_HOSTED_ONE_TIME_KEYS - held.oneTimeKeys)
    val missing = room - device.oneTimeKeys().size
    if (missing > 0) device.generateOneTimeKeys(missing.toULong())
    if (!held.fallback && device.fallbackKey() == null) device.generateFallbackKey()

    val otks = device.oneTimeKeys().take(room).map { signedKey(device, it) }
    val fallback = device.fallbackKey()?.let { signedKey(device, it) }
    return if (otks.isEmpty() && fallback == null) null else E2eKeysUploadDto(otks, fallback)
}
