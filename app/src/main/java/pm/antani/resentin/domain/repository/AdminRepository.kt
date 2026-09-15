package pm.antani.resentin.domain.repository

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pm.antani.resentin.net.dto.AddressingSettingsAdminDto
import pm.antani.resentin.net.dto.CredentialAdminDto
import pm.antani.resentin.net.dto.CredentialCreateRequestDto
import pm.antani.resentin.net.dto.NetworkAdminDto
import pm.antani.resentin.net.dto.NetworkCreateRequestDto
import pm.antani.resentin.net.dto.ReaperRunResultDto
import pm.antani.resentin.net.dto.ServerAdminDto
import pm.antani.resentin.net.dto.ServerCreateRequestDto
import pm.antani.resentin.net.dto.SessionAdminDto
import pm.antani.resentin.net.dto.SessionLogEntryDto
import pm.antani.resentin.net.dto.SettingsAdminDto
import pm.antani.resentin.net.dto.UploadAdminDto
import pm.antani.resentin.net.dto.UploadSettingsAdminDto
import pm.antani.resentin.net.dto.UserAdminDto
import pm.antani.resentin.net.dto.UserAdminFlagsRequestDto
import pm.antani.resentin.net.dto.UserCreateRequestDto
import pm.antani.resentin.net.dto.UserPasswordRequestDto
import pm.antani.resentin.net.dto.VhostAdminDto
import pm.antani.resentin.net.dto.VhostCreateRequestDto
import pm.antani.resentin.net.dto.VhostsAdminEnvelopeDto
import pm.antani.resentin.net.dto.VisitorAdminDto
import pm.antani.resentin.net.rest.AdminApi

/** Thin wrapper over the operator-console REST surface (every `/admin` route) — every call
 * here can 403 for a non-admin subject; that's surfaced as an ordinary [Result]
 * failure, nothing special-cased, since [pm.antani.resentin.ui.admin.AdminScreen]
 * is only reachable once `GET /me` already confirmed `is_admin`. */
class AdminRepository(private val authRepository: AuthRepository) {

    private fun api() = authRepository.api(AdminApi::class.java)

    suspend fun getNetworks(): Result<List<NetworkAdminDto>> = runCatching { api().getNetworks().networks }

    suspend fun createNetwork(slug: String): Result<NetworkAdminDto> = runCatching {
        val response = api().createNetwork(NetworkCreateRequestDto(slug = slug))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        checkNotNull(response.body())
    }

    /** [maxConcurrentVisitorSessions]/[maxConcurrentUserSessions]/[maxPerIp]: `null` is a
     * meaningful "clear this cap to unlimited" — the server's three-valued contract
     * (`nil` clears, `0` locks down, `N>0` caps at N) — not "leave unchanged"; every
     * cap is always sent on every call. See [pm.antani.resentin.net.rest.AdminApi.updateNetwork]
     * for why this builds the body by hand instead of a normal `@Serializable` DTO. */
    suspend fun updateNetwork(
        slug: String,
        visitorEnabled: Boolean,
        visitorAutoconnect: Boolean,
        maxConcurrentVisitorSessions: Int?,
        maxConcurrentUserSessions: Int?,
        maxPerIp: Int?,
    ): Result<NetworkAdminDto> = runCatching {
        val body = buildJsonObject {
            put("visitor_enabled", visitorEnabled)
            put("visitor_autoconnect", visitorAutoconnect)
            put("max_concurrent_visitor_sessions", maxConcurrentVisitorSessions?.let { JsonPrimitive(it) } ?: JsonNull)
            put("max_concurrent_user_sessions", maxConcurrentUserSessions?.let { JsonPrimitive(it) } ?: JsonNull)
            put("max_per_ip", maxPerIp?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        val response = api().updateNetwork(slug, body)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        checkNotNull(response.body())
    }

    suspend fun deleteNetwork(id: Int): Result<Unit> = runCatching {
        val response = api().deleteNetwork(id)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun getServers(networkId: Int): Result<List<ServerAdminDto>> = runCatching { api().getServers(networkId).servers }

    suspend fun createServer(networkId: Int, host: String, port: Int, tls: Boolean): Result<ServerAdminDto> = runCatching {
        val response = api().createServer(networkId, ServerCreateRequestDto(host, port, tls))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        checkNotNull(response.body())
    }

    suspend fun deleteServer(networkId: Int, id: Int): Result<Unit> = runCatching {
        val response = api().deleteServer(networkId, id)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun getVhosts(): Result<VhostsAdminEnvelopeDto> = runCatching { api().getVhosts() }

    suspend fun createVhost(address: String, inPool: Boolean, generallyAvailable: Boolean): Result<VhostAdminDto> = runCatching {
        val response = api().createVhost(VhostCreateRequestDto(address, inPool, generallyAvailable))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        checkNotNull(response.body())
    }

    suspend fun deleteVhost(id: Int): Result<Unit> = runCatching {
        val response = api().deleteVhost(id)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun getUsers(): Result<List<UserAdminDto>> = runCatching { api().getUsers().users }

    suspend fun createUser(name: String, password: String, isAdmin: Boolean): Result<UserAdminDto> = runCatching {
        val response = api().createUser(UserCreateRequestDto(name, password, isAdmin))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        checkNotNull(response.body())
    }

    suspend fun setUserAdmin(id: String, isAdmin: Boolean): Result<UserAdminDto> = runCatching {
        val response = api().updateUserAdminFlag(id, UserAdminFlagsRequestDto(isAdmin))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        checkNotNull(response.body())
    }

    suspend fun setUserPassword(id: String, password: String): Result<UserAdminDto> = runCatching {
        val response = api().updateUserPassword(id, UserPasswordRequestDto(password))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        checkNotNull(response.body())
    }

    suspend fun deleteUser(id: String): Result<Unit> = runCatching {
        val response = api().deleteUser(id)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun getSessions(): Result<List<SessionAdminDto>> = runCatching { api().getSessions().sessions }

    suspend fun disconnectSession(compositeId: String): Result<Unit> = runCatching {
        val response = api().disconnectSession(compositeId)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun killSession(compositeId: String): Result<Unit> = runCatching {
        val response = api().killSession(compositeId)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun getVisitors(): Result<List<VisitorAdminDto>> = runCatching { api().getVisitors().visitors }

    suspend fun deleteVisitor(id: String): Result<Unit> = runCatching {
        val response = api().deleteVisitor(id)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun runReaper(): Result<ReaperRunResultDto> = runCatching {
        val response = api().runReaper()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        checkNotNull(response.body())
    }

    // --- Credentials (per-user network access) -------------------------------

    suspend fun getCredentials(): Result<List<CredentialAdminDto>> = runCatching { api().getCredentials().credentials }

    suspend fun createCredential(
        userId: String,
        networkId: Int,
        nick: String,
        authMethod: String,
        password: String?,
    ): Result<CredentialAdminDto> = runCatching {
        val response = api().createCredential(CredentialCreateRequestDto(userId, networkId, nick, authMethod, password))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        checkNotNull(response.body())
    }

    suspend fun deleteCredential(userId: String, networkId: Int): Result<Unit> = runCatching {
        val response = api().deleteCredential(userId, networkId)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    // --- Settings (upload + addressing) --------------------------------------

    suspend fun getSettings(): Result<SettingsAdminDto> = runCatching { api().getSettings().settings }

    /** Every field always sent explicitly (`null` = clear), same three-valued
     * reasoning as [updateNetwork] — see [pm.antani.resentin.net.rest.AdminApi.updateSettings]. */
    suspend fun updateSettings(upload: UploadSettingsAdminDto, addressing: AddressingSettingsAdminDto): Result<SettingsAdminDto> =
        runCatching {
            val body = buildJsonObject {
                put(
                    "upload",
                    buildJsonObject {
                        put("active_host", upload.activeHost)
                        put("image_per_file_cap_bytes", upload.imagePerFileCapBytes?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("video_per_file_cap_bytes", upload.videoPerFileCapBytes?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("document_per_file_cap_bytes", upload.documentPerFileCapBytes?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("audio_per_file_cap_bytes", upload.audioPerFileCapBytes?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("global_cap_bytes", upload.globalCapBytes?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("video_max_duration_seconds", upload.videoMaxDurationSeconds?.let { JsonPrimitive(it) } ?: JsonNull)
                    },
                )
                put(
                    "addressing",
                    buildJsonObject {
                        put("mode", addressing.mode)
                        put("static_mapping_prefix", addressing.staticMappingPrefix?.let { JsonPrimitive(it) } ?: JsonNull)
                    },
                )
            }
            val response = api().updateSettings(body)
            check(response.isSuccessful) { "HTTP ${response.code()}" }
            checkNotNull(response.body()).settings
        }

    // --- Uploads registry ------------------------------------------------------

    suspend fun getUploads(): Result<List<UploadAdminDto>> = runCatching { api().getUploads().uploads }

    suspend fun deleteUpload(id: Int): Result<Unit> = runCatching {
        val response = api().deleteUpload(id)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    // --- Session log -------------------------------------------------------------

    suspend fun getSessionLog(limit: Int? = null): Result<List<SessionLogEntryDto>> = runCatching { api().getSessionLog(limit).sessionLog }

    suspend fun getSessionLogSessions(limit: Int? = null): Result<List<SessionLogEntryDto>> =
        runCatching { api().getSessionLogSessions(limit).sessionLogSessions }
}
