package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class UploadResponseDto(
    val slug: String,
    val url: String,
    val expiresAt: String,
)
