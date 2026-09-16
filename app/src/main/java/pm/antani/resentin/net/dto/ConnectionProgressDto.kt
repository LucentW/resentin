package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

/** Upstream connection lifecycle ping from the per-user WebSocket topic. */
@Serializable
data class ConnectionProgressDto(
    val network: String,
    val state: String,
)

/** One step of the server-driven credential recovery flow. */
@Serializable
data class RecoverProgressDto(
    val network: String,
    val step: String,
    val status: String,
    val reason: String? = null,
)

/** Terminal result of the server-driven credential recovery flow. */
@Serializable
data class RecoverResultDto(
    val network: String,
    val outcome: String,
    val reason: String? = null,
)
