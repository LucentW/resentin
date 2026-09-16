package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class DirectoryProgressDto(
    val network: String,
    val count: Int,
)

@Serializable
data class DirectoryCompleteDto(
    val network: String,
    val total: Int,
)

@Serializable
data class DirectoryFailedDto(
    val network: String,
    val reason: String,
)
