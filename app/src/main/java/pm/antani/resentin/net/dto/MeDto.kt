package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

/** `GET /me` is a discriminated union: a registered `user` carries a top-level
 * `name`, but a `visitor` never does — identity is per-network there, so the
 * nick lives on the first `home_data.networks` row instead (see grappa's
 * `MeJSON` moduledoc). Both fields are therefore optional; callers resolve
 * the display name via [MeDto.displayName]. */
@Serializable
data class MeDto(
    val kind: String? = null,
    val id: String? = null,
    val name: String? = null,
    val homeData: MeHomeDataDto? = null,
    val isAdmin: Boolean = false,
) {
    val displayName: String?
        get() = name ?: homeData?.networks?.firstOrNull { !it.nick.isNullOrBlank() }?.nick

    /** The identifier the server's `GrappaChannel.authorize/2` actually checks
     * `socket.assigns.user_name` against — NOT the same as [displayName]. A user
     * socket assigns its account `name` (which happens to equal [displayName] for
     * users), but a visitor socket assigns `"visitor:" <> visitor.id`
     * (`UserSocket.connect/3`), completely unrelated to the visitor's chosen nick.
     * Every `grappa:user:{subject}/...` topic must be built from THIS value, or
     * every join on it is silently rejected `{"error":"forbidden"}` — no exception,
     * no toast, just permanently empty topic/modes/members/live-messages. */
    val subject: String?
        get() = when (kind) {
            "visitor" -> id?.let { "visitor:$it" }
            else -> name
        }
}

@Serializable
data class MeHomeDataDto(
    val networks: List<MeHomeNetworkDto> = emptyList(),
)

@Serializable
data class MeHomeNetworkDto(
    val nick: String? = null,
)
