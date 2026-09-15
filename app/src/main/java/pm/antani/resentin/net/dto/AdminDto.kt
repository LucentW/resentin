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

@Serializable
data class ServersAdminEnvelopeDto(val servers: List<ServerAdminDto> = emptyList())

// --- Vhosts (admin inventory) --------------------------------------------

@Serializable
data class VhostAdminDto(
    val id: Int,
    val address: String,
    val inPool: Boolean = false,
    val generallyAvailable: Boolean = false,
)

@Serializable
data class VhostsAdminEnvelopeDto(
    val vhosts: List<VhostAdminDto> = emptyList(),
    // The host's actual egressable addresses (getifaddrs/0, loopback/link-local
    // filtered) — lets the "add vhost" UI offer a picker of addresses that can
    // really be bound, like cicchetto does, instead of a free-text field a typo
    // in which only surfaces as a bind failure once something tries to use it.
    val hostCandidates: List<String> = emptyList(),
)

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

// --- Credentials (per-user network access) ---------------------------------

/** One (user, network) IRC-credential binding. Trimmed to the fields the
 * admin UI actually renders — the server's row carries more (autojoin
 * channels, live process introspection, ...), harmlessly dropped by
 * `ignoreUnknownKeys`. */
@Serializable
data class CredentialAdminDto(
    val userId: String,
    val networkId: Int,
    val networkSlug: String,
    val nick: String,
    val authMethod: String,
    val connectionState: String? = null,
)

@Serializable
data class CredentialsAdminEnvelopeDto(val credentials: List<CredentialAdminDto> = emptyList())

@Serializable
data class CredentialCreateRequestDto(
    val userId: String,
    val networkId: Int,
    val nick: String,
    val authMethod: String,
    val password: String? = null,
)

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

// --- Settings (upload + addressing) -----------------------------------------

@Serializable
data class UploadSettingsAdminDto(
    val activeHost: String = "embedded",
    val imagePerFileCapBytes: Long? = null,
    val videoPerFileCapBytes: Long? = null,
    val documentPerFileCapBytes: Long? = null,
    val audioPerFileCapBytes: Long? = null,
    val globalCapBytes: Long? = null,
    val videoMaxDurationSeconds: Int? = null,
)

@Serializable
data class AddressingSettingsAdminDto(
    val mode: String = "pool_with_reservations",
    val staticMappingPrefix: String? = null,
)

@Serializable
data class SettingsAdminDto(
    val upload: UploadSettingsAdminDto = UploadSettingsAdminDto(),
    val addressing: AddressingSettingsAdminDto = AddressingSettingsAdminDto(),
)

@Serializable
data class SettingsEnvelopeDto(val settings: SettingsAdminDto = SettingsAdminDto())

// --- Uploads registry --------------------------------------------------------

@Serializable
data class UploadAdminDto(
    val id: Int,
    val slug: String,
    val mime: String? = null,
    val bytes: Long = 0,
    val originalFilename: String? = null,
    val subjectKind: String? = null,
    val subjectId: String? = null,
)

@Serializable
data class UploadsAdminEnvelopeDto(
    val uploads: List<UploadAdminDto> = emptyList(),
    val liveBytesSum: Long = 0,
    val globalCapBytes: Long = 0,
)

// --- Session log --------------------------------------------------------------

@Serializable
data class SessionLogEntryDto(
    val sessionId: String,
    val event: String,
    val subjectKind: String,
    val networkId: Int? = null,
    val networkSlug: String? = null,
    val nick: String? = null,
    val oldNick: String? = null,
    val reason: String? = null,
    val clean: Boolean? = null,
    val durationMs: Long? = null,
    val at: String,
)

@Serializable
data class SessionLogEnvelopeDto(val sessionLog: List<SessionLogEntryDto> = emptyList())

@Serializable
data class SessionLogSessionsEnvelopeDto(val sessionLogSessions: List<SessionLogEntryDto> = emptyList())
