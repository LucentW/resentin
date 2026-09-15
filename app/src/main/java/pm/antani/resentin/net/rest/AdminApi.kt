package pm.antani.resentin.net.rest

import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import pm.antani.resentin.net.dto.CredentialAdminDto
import pm.antani.resentin.net.dto.CredentialCreateRequestDto
import pm.antani.resentin.net.dto.CredentialsAdminEnvelopeDto
import pm.antani.resentin.net.dto.NetworkAdminDto
import pm.antani.resentin.net.dto.NetworkCreateRequestDto
import pm.antani.resentin.net.dto.NetworksAdminEnvelopeDto
import pm.antani.resentin.net.dto.ReaperRunResultDto
import pm.antani.resentin.net.dto.ServerAdminDto
import pm.antani.resentin.net.dto.ServerCreateRequestDto
import pm.antani.resentin.net.dto.ServersAdminEnvelopeDto
import pm.antani.resentin.net.dto.SessionLogEnvelopeDto
import pm.antani.resentin.net.dto.SessionLogSessionsEnvelopeDto
import pm.antani.resentin.net.dto.SessionsAdminEnvelopeDto
import pm.antani.resentin.net.dto.SettingsEnvelopeDto
import pm.antani.resentin.net.dto.UploadsAdminEnvelopeDto
import pm.antani.resentin.net.dto.UserAdminDto
import pm.antani.resentin.net.dto.UserAdminFlagsRequestDto
import pm.antani.resentin.net.dto.UserCreateRequestDto
import pm.antani.resentin.net.dto.UserPasswordRequestDto
import pm.antani.resentin.net.dto.UsersAdminEnvelopeDto
import pm.antani.resentin.net.dto.VhostAdminDto
import pm.antani.resentin.net.dto.VhostCreateRequestDto
import pm.antani.resentin.net.dto.VhostsAdminEnvelopeDto
import pm.antani.resentin.net.dto.VisitorsAdminEnvelopeDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Operator-console surface (every `/admin` route), gated server-side on
 * `{:user, %User{is_admin: true}}` — a non-admin (or a visitor) gets a uniform
 * 403 on every one of these, which [pm.antani.resentin.domain.repository.AdminRepository]
 * surfaces as an ordinary [Result] failure rather than something special-cased. */
interface AdminApi {
    @GET("admin/networks")
    suspend fun getNetworks(): NetworksAdminEnvelopeDto

    @POST("admin/networks")
    suspend fun createNetwork(@Body body: NetworkCreateRequestDto): Response<NetworkAdminDto>

    /** Body is a hand-built [JsonObject] (not a fixed DTO) because a cap field needs
     * three distinct wire states — "set to N", "explicit null (clear to unlimited)",
     * and "key absent (leave unchanged)" — and the app's shared `AppJson` config sets
     * `explicitNulls = false`, which would silently turn "clear this cap" into "leave
     * it unchanged" for a normal data-class body. See [pm.antani.resentin.domain.repository.AdminRepository.updateNetwork]. */
    @PATCH("admin/networks/{slug}")
    suspend fun updateNetwork(@Path("slug") slug: String, @Body body: JsonObject): Response<NetworkAdminDto>

    @DELETE("admin/networks/{id}")
    suspend fun deleteNetwork(@Path("id") id: Int): Response<ResponseBody>

    @GET("admin/networks/{networkId}/servers")
    suspend fun getServers(@Path("networkId") networkId: Int): ServersAdminEnvelopeDto

    @POST("admin/networks/{networkId}/servers")
    suspend fun createServer(@Path("networkId") networkId: Int, @Body body: ServerCreateRequestDto): Response<ServerAdminDto>

    @DELETE("admin/networks/{networkId}/servers/{id}")
    suspend fun deleteServer(@Path("networkId") networkId: Int, @Path("id") id: Int): Response<ResponseBody>

    @GET("admin/vhosts")
    suspend fun getVhosts(): VhostsAdminEnvelopeDto

    @POST("admin/vhosts")
    suspend fun createVhost(@Body body: VhostCreateRequestDto): Response<VhostAdminDto>

    @DELETE("admin/vhosts/{id}")
    suspend fun deleteVhost(@Path("id") id: Int): Response<ResponseBody>

    @GET("admin/users")
    suspend fun getUsers(): UsersAdminEnvelopeDto

    @POST("admin/users")
    suspend fun createUser(@Body body: UserCreateRequestDto): Response<UserAdminDto>

    @PATCH("admin/users/{id}")
    suspend fun updateUserAdminFlag(@Path("id") id: String, @Body body: UserAdminFlagsRequestDto): Response<UserAdminDto>

    @PUT("admin/users/{id}/password")
    suspend fun updateUserPassword(@Path("id") id: String, @Body body: UserPasswordRequestDto): Response<UserAdminDto>

    @DELETE("admin/users/{id}")
    suspend fun deleteUser(@Path("id") id: String): Response<ResponseBody>

    @GET("admin/sessions")
    suspend fun getSessions(): SessionsAdminEnvelopeDto

    @POST("admin/sessions/{id}/disconnect")
    suspend fun disconnectSession(@Path("id") id: String): Response<ResponseBody>

    @DELETE("admin/sessions/{id}")
    suspend fun killSession(@Path("id") id: String): Response<ResponseBody>

    @GET("admin/visitors")
    suspend fun getVisitors(): VisitorsAdminEnvelopeDto

    @DELETE("admin/visitors/{id}")
    suspend fun deleteVisitor(@Path("id") id: String): Response<ResponseBody>

    @POST("admin/reaper/run")
    suspend fun runReaper(): Response<ReaperRunResultDto>

    @GET("admin/credentials")
    suspend fun getCredentials(): CredentialsAdminEnvelopeDto

    @POST("admin/credentials")
    suspend fun createCredential(@Body body: CredentialCreateRequestDto): Response<CredentialAdminDto>

    @DELETE("admin/credentials/{userId}/{networkId}")
    suspend fun deleteCredential(@Path("userId") userId: String, @Path("networkId") networkId: Int): Response<ResponseBody>

    @GET("admin/settings")
    suspend fun getSettings(): SettingsEnvelopeDto

    /** Body is a hand-built [JsonObject], same reasoning as [updateNetwork]: an
     * emptied cap or `static_mapping_prefix` field needs to reach the server as
     * an explicit `null` (clear), which a normal `explicitNulls = false` DTO body
     * would silently drop instead. See [pm.antani.resentin.domain.repository.AdminRepository.updateSettings]. */
    @PUT("admin/settings")
    suspend fun updateSettings(@Body body: JsonObject): Response<SettingsEnvelopeDto>

    @GET("admin/uploads")
    suspend fun getUploads(): UploadsAdminEnvelopeDto

    @DELETE("admin/uploads/{id}")
    suspend fun deleteUpload(@Path("id") id: Int): Response<ResponseBody>

    @GET("admin/session_log")
    suspend fun getSessionLog(@Query("limit") limit: Int? = null): SessionLogEnvelopeDto

    @GET("admin/session_log/sessions")
    suspend fun getSessionLogSessions(@Query("limit") limit: Int? = null): SessionLogSessionsEnvelopeDto
}
