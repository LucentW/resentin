package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class ArchiveEntryDto(
    val target: String,
    /** `"channel"` or `"query"`. */
    val kind: String,
    /** Epoch MILLISECONDS of the most recent scrollback row for this target
     * (`max(server_time)`, and `server_time` is milliseconds — see grappa's
     * `Grappa.Scrollback.Message` schema doc). */
    val lastActivity: Long,
    val rowCount: Int,
)

@Serializable
data class ArchiveEnvelopeDto(
    val archive: List<ArchiveEntryDto> = emptyList(),
)
