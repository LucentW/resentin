package pm.antani.resentin.domain.repository

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pm.antani.resentin.net.dto.NetworkAdminDto
import pm.antani.resentin.net.dto.NetworkCreateRequestDto
import pm.antani.resentin.net.dto.ReaperRunResultDto
import pm.antani.resentin.net.dto.ServerAdminDto
import pm.antani.resentin.net.dto.ServerCreateRequestDto
import pm.antani.resentin.net.dto.SessionAdminDto
import pm.antani.resentin.net.dto.UserAdminDto
import pm.antani.resentin.net.dto.UserAdminFlagsRequestDto
import pm.antani.resentin.net.dto.UserCreateRequestDto
import pm.antani.resentin.net.dto.UserPasswordRequestDto
import pm.antani.resentin.net.dto.VhostAdminDto
import pm.antani.resentin.net.dto.VhostCreateRequestDto
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

    suspend fun createServer(networkId: Int, host: String, port: Int, tls: Boolean): Result<ServerAdminDto> = runCatching {
        val response = api().createServer(networkId, ServerCreateRequestDto(host, port, tls))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        checkNotNull(response.body())
    }

    suspend fun getVhosts(): Result<List<VhostAdminDto>> = runCatching { api().getVhosts().vhosts }

    suspend fun createVhost(address: String): Result<VhostAdminDto> = runCatching {
        val response = api().createVhost(VhostCreateRequestDto(address = address))
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
}
