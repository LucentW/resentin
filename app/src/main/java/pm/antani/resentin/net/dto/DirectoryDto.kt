package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class DirectoryEntryDto(
    val name: String,
    val topic: String? = null,
    val userCount: Int = 0,
    val featured: Boolean = false,
)

@Serializable
data class DirectoryPageDto(
    val entries: List<DirectoryEntryDto> = emptyList(),
    val nextCursor: String? = null,
    val total: Int = 0,
    val capturedAt: String? = null,
    /** One of "fresh" | "stale" | "empty" | "refreshing" — see
     * `Grappa.ChannelDirectory.status/0` on the server. */
    val status: String = "empty",
)

@Serializable
data class JoinChannelRequestDto(
    val name: String,
    val key: String? = null,
)
