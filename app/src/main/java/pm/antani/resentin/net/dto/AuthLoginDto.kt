package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthLoginRequestDto(
    val identifier: String,
    // Absent (rather than an empty string) is what routes a nick with no matching
    // account to grappa-irc's anonymous visitor login — an empty string would
    // instead be charged against the visitor-login password-guess throttle. See
    // AuthRepository.loginAsVisitor.
    val password: String? = null,
)

@Serializable
data class AuthLoginResponseDto(
    val token: String? = null,
)

/** Body for `POST /auth/share/consume` — cicchetto's session-sharing QR/link
 * redemption. The response rides the SAME `AuthLoginResponseDto` shape (grappa-irc's
 * `GrappaWeb.AuthJSON.login/1` renders both), so no separate response DTO is needed. */
@Serializable
data class ShareConsumeRequestDto(
    val token: String,
)
