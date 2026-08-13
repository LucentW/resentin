package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

// --- Networks -----------------------------------------------------------

@Serializable
data class NetworkAdminDto(
    val id: Int,
    val slug: String,
    val servicesFlavor: String? = null,
    val visitorEnabled: Boolean = false,
    val visitorAutoconnect: Boolean = false,
    val maxConcurrentVisitorSessions: Int? = null,
    val maxConcurrentUserSessions: Int? = null,
    val maxPerIp: Int? = null,
)

@Serializable
data class NetworksAdminEnvelopeDto(val networks: List<NetworkAdminDto> = emptyList())

@Serializable
data class NetworkCreateRequestDto(
    val slug: String,
    val maxConcurrentVisitorSessions: Int? = null,
    val maxConcurrentUserSessions: Int? = null,
    val maxPerIp: Int? = null,
)

@Serializable
data class NetworkUpdateRequestDto(
    val visitorEnabled: Boolean,
    val visitorAutoconnect: Boolean,
)

@Serializable
data class ServerAdminDto(
    val id: Int,
    val networkId: Int,
    val host: String,
    val port: Int,
    val tls: Boolean = false,
    val priority: Int = 0,
    val enabled: Boolean = true,
)

@Serializable
data class ServerCreateRequestDto(
    val host: String,
    val port: Int,
    val tls: Boolean = true,
)

// --- Vhosts (admin inventory) --------------------------------------------

@Serializable
data class VhostAdminDto(
    val id: Int,
    val address: String,
    val inPool: Boolean = false,
    val generallyAvailable: Boolean = false,
)

@Serializable
data class VhostsAdminEnvelopeDto(val vhosts: List<VhostAdminDto> = emptyList())

@Serializable
data class VhostCreateRequestDto(
    val address: String,
    val inPool: Boolean = false,
    val generallyAvailable: Boolean = true,
)

// --- Users ----------------------------------------------------------------

@Serializable
data class UserAdminDto(
    val id: String,
    val name: String,
    val isAdmin: Boolean = false,
    val liveSessionCount: Int = 0,
)

@Serializable
data class UsersAdminEnvelopeDto(val users: List<UserAdminDto> = emptyList())

@Serializable
data class UserCreateRequestDto(
    val name: String,
    val password: String,
    val isAdmin: Boolean = false,
)

@Serializable
data class UserAdminFlagsRequestDto(val isAdmin: Boolean)

@Serializable
data class UserPasswordRequestDto(val password: String)

// --- Sessions (live) --------------------------------------------------------

@Serializable
data class SessionLiveStateDto(
    val nick: String? = null,
    val alive: Boolean = false,
    val joinedChannels: List<String> = emptyList(),
    val peerAddress: String? = null,
)

@Serializable
data class SessionAdminDto(
    val subjectKind: String,
    val subjectId: String,
    val subjectLabel: String? = null,
    val networkId: Int,
    val liveState: SessionLiveStateDto? = null,
) {
    /** The composite `"<subject_kind>:<subject_id>:<network_id>"` the admin REST
     * surface uses as its session `:id` path segment. */
    val compositeId: String get() = "$subjectKind:$subjectId:$networkId"
}

@Serializable
data class SessionsAdminEnvelopeDto(val sessions: List<SessionAdminDto> = emptyList())

// --- Visitors + reaper ------------------------------------------------------

@Serializable
data class VisitorAdminDto(
    val id: String,
    val ip: String? = null,
    val identified: Boolean = false,
    val expiresAt: String? = null,
    val lastSeenAt: String? = null,
)

@Serializable
data class VisitorsAdminEnvelopeDto(val visitors: List<VisitorAdminDto> = emptyList())

@Serializable
data class ReaperRunResultDto(
    val sweptCount: Int = 0,
    val sweptAt: String? = null,
)
