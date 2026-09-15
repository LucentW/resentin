package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

/** #2095 — the subject's stored upload-TTL preference, in seconds. `null` =
 * no preference: fall back to the active (embedded) host's default. Same
 * GET/PUT shape both ways: `{"upload_ttl_seconds": N | null}`. Only used to
 * DECODE here — PUT with an explicit `null` (clear to site default) needs a
 * hand-built JsonObject since `AppJson`'s `explicitNulls = false` would drop
 * the key (same trap as `updateAutoAwayDebounce`). */
@Serializable
data class UploadTtlSecondsDto(
    val uploadTtlSeconds: Int? = null,
)

/** #1883 — the pre-upload confirm opt-in. Off by default; read by the client
 * at upload time (no session involvement server-side). Same GET/PUT shape
 * both ways: `{"upload_confirm_enabled": bool}`. */
@Serializable
data class UploadConfirmEnabledDto(
    val uploadConfirmEnabled: Boolean = false,
)

/** Ladder the embedded host (`POST /api/uploads` `expire` field) accepts —
 * anything off-ladder answers 400. Seconds are the currency on the wire. */
val UPLOAD_TTL_LADDER_SECONDS = listOf(3600, 43200, 86400, 259200)

/** Server default applied when `expire` is omitted. */
const val UPLOAD_TTL_DEFAULT_SECONDS = 86400

/** What the next upload expires after with nobody choosing anything: the
 * stored preference when it is on the host ladder, the host default
 * otherwise. Mirrors cicchetto's `effectiveTtlSeconds` — a preference the
 * active host cannot serve must not seed a picker with an option it lacks. */
fun effectiveUploadTtlSeconds(preference: Int?): Int =
    if (preference != null && preference in UPLOAD_TTL_LADDER_SECONDS) {
        preference
    } else {
        UPLOAD_TTL_DEFAULT_SECONDS
    }
