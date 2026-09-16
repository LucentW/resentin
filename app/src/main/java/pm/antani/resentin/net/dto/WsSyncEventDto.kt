package pm.antani.resentin.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** User-topic events that invalidate or update shared session state. */
@Serializable
data class PeerAwayDto(
    val network: String,
    val peer: String,
    val message: String,
)

@Serializable
data class JoinFailedDto(
    val network: String,
    val channel: String,
    val state: String = "failed",
    val reason: String? = null,
    val numeric: Int? = null,
)

@Serializable
data class KickedDto(
    val network: String,
    val channel: String,
    val state: String = "kicked",
    val by: String? = null,
    val reason: String? = null,
)

@Serializable
data class ArchiveChangedDto(
    @SerialName("network_slug") val networkSlug: String,
)

@Serializable
data class ArchivePurgedDto(
    @SerialName("network_slug") val networkSlug: String,
    val target: String,
)
