package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

/** `GET /me` is a discriminated union: a registered `user` carries a top-level
 * `name`, but a `visitor` never does — identity is per-network there, so the
 * nick lives on the first `home_data.networks` row instead (see grappa's
 * `MeJSON` moduledoc). Both fields are therefore optional; callers resolve
 * the display name via [MeDto.displayName]. */
@Serializable
data class MeDto(
    val name: String? = null,
    val homeData: MeHomeDataDto? = null,
    val isAdmin: Boolean = false,
) {
    val displayName: String?
        get() = name ?: homeData?.networks?.firstOrNull { !it.nick.isNullOrBlank() }?.nick
}

@Serializable
data class MeHomeDataDto(
    val networks: List<MeHomeNetworkDto> = emptyList(),
)

@Serializable
data class MeHomeNetworkDto(
    val nick: String? = null,
)
