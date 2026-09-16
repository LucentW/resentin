package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

/** Upstream connection lifecycle ping from the per-user WebSocket topic. */
@Serializable
data class ConnectionProgressDto(
    val network: String,
    val state: String,
)
