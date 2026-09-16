package pm.antani.resentin.net.rest

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import pm.antani.resentin.net.dto.ConnectionStateUpdateDto
import pm.antani.resentin.net.dto.IdentityUpdateDto
import pm.antani.resentin.net.dto.NickUpdateDto
import pm.antani.resentin.net.dto.NetworkPasswordUpdateDto
import pm.antani.resentin.net.dto.PerformDto
import pm.antani.resentin.net.dto.PerformUpdateDto
import pm.antani.resentin.net.dto.ProfileUpdateDto
import pm.antani.resentin.net.dto.ServerPassDto
import pm.antani.resentin.net.dto.ServerPassUpdateDto
import pm.antani.resentin.net.dto.TopicUpdateDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface NetworkSettingsApi {
    /** Live IRC NICK command, without changing the configured identity. */
    @POST("networks/{slug}/nick")
    suspend fun postNick(@Path("slug") slug: String, @Body body: NickUpdateDto): Response<ResponseBody>

    @PATCH("networks/{slug}/identity")
    suspend fun updateIdentity(@Path("slug") slug: String, @Body body: IdentityUpdateDto): Response<ResponseBody>

    /** Write-only NickServ password; the response never contains the secret. */
    @PUT("networks/{slug}/password")
    suspend fun updatePassword(@Path("slug") slug: String, @Body body: NetworkPasswordUpdateDto): Response<ResponseBody>

    /** Server PASS set-ness; the secret itself is never returned. */
    @GET("networks/{slug}/server_pass")
    suspend fun getServerPass(@Path("slug") slug: String): ServerPassDto

    @PUT("networks/{slug}/server_pass")
    suspend fun updateServerPass(@Path("slug") slug: String, @Body body: ServerPassUpdateDto): ServerPassDto

    // KVIrc-style CTCP USERINFO profile (age/gender/location/languages/custom) — never
    // bounces the live connection, unlike /identity above.
    @PATCH("networks/{slug}/profile")
    suspend fun updateProfile(@Path("slug") slug: String, @Body body: ProfileUpdateDto): Response<ResponseBody>

    // M3a — own avatar, sibling of /profile in the same never-bounces sense.
    @Multipart
    @PUT("networks/{slug}/avatar")
    suspend fun uploadAvatar(@Path("slug") slug: String, @Part file: MultipartBody.Part): Response<ResponseBody>

    @DELETE("networks/{slug}/avatar")
    suspend fun deleteAvatar(@Path("slug") slug: String): Response<ResponseBody>

    @PATCH("networks/{slug}")
    suspend fun updateConnectionState(
        @Path("slug") slug: String,
        @Body body: ConnectionStateUpdateDto,
    ): Response<ResponseBody>

    @GET("networks/{slug}/perform")
    suspend fun getPerform(@Path("slug") slug: String): PerformDto

    @PUT("networks/{slug}/perform")
    suspend fun updatePerform(@Path("slug") slug: String, @Body body: PerformUpdateDto): PerformDto

    @POST("networks/{slug}/channels/{channel}/topic")
    suspend fun updateTopic(
        @Path("slug") slug: String,
        @Path("channel") channel: String,
        @Body body: TopicUpdateDto,
    ): Response<ResponseBody>

    @DELETE("networks/{slug}/channels/{channel}")
    suspend fun partChannel(
        @Path("slug") slug: String,
        @Path("channel") channel: String,
        @Query("reason") reason: String? = null,
    ): Response<ResponseBody>
}
