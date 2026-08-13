package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class ArchiveEntryDto(
    val target: String,
    /** `"channel"` or `"query"`. */
    val kind: String,
    /** Epoch seconds of the most recent scrollback row for this target. */
    val lastActivity: Long,
    val rowCount: Int,
)

@Serializable
data class ArchiveEnvelopeDto(
    val archive: List<ArchiveEntryDto> = emptyList(),
)
