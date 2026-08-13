package pm.antani.resentin.net.rest

import okhttp3.ResponseBody
import pm.antani.resentin.net.dto.NetworkAdminDto
import pm.antani.resentin.net.dto.NetworkCreateRequestDto
import pm.antani.resentin.net.dto.NetworkUpdateRequestDto
import pm.antani.resentin.net.dto.NetworksAdminEnvelopeDto
import pm.antani.resentin.net.dto.ReaperRunResultDto
import pm.antani.resentin.net.dto.ServerAdminDto
import pm.antani.resentin.net.dto.ServerCreateRequestDto
import pm.antani.resentin.net.dto.SessionsAdminEnvelopeDto
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

/** Operator-console surface (every `/admin` route), gated server-side on
 * `{:user, %User{is_admin: true}}` — a non-admin (or a visitor) gets a uniform
 * 403 on every one of these, which [pm.antani.resentin.domain.repository.AdminRepository]
 * surfaces as an ordinary [Result] failure rather than something special-cased. */
interface AdminApi {
    @GET("admin/networks")
    suspend fun getNetworks(): NetworksAdminEnvelopeDto

    @POST("admin/networks")
    suspend fun createNetwork(@Body body: NetworkCreateRequestDto): Response<NetworkAdminDto>

    @PATCH("admin/networks/{slug}")
    suspend fun updateNetwork(@Path("slug") slug: String, @Body body: NetworkUpdateRequestDto): Response<NetworkAdminDto>

    @DELETE("admin/networks/{id}")
    suspend fun deleteNetwork(@Path("id") id: Int): Response<ResponseBody>

    @POST("admin/networks/{networkId}/servers")
    suspend fun createServer(@Path("networkId") networkId: Int, @Body body: ServerCreateRequestDto): Response<ServerAdminDto>

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
}
