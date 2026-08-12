package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class RateLimitErrorDto(
    val error: String? = null,
    val retryAfterMs: Long? = null,
)
