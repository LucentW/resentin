package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthLoginRequestDto(
    val identifier: String,
    val password: String,
)

@Serializable
data class AuthLoginResponseDto(
    val token: String? = null,
)
