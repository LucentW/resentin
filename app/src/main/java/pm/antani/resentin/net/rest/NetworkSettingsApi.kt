package pm.antani.resentin.net.rest

import okhttp3.ResponseBody
import pm.antani.resentin.net.dto.ConnectionStateUpdateDto
import pm.antani.resentin.net.dto.IdentityUpdateDto
import pm.antani.resentin.net.dto.PerformDto
import pm.antani.resentin.net.dto.PerformUpdateDto
import pm.antani.resentin.net.dto.TopicUpdateDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface NetworkSettingsApi {
    @PATCH("networks/{slug}/identity")
    suspend fun updateIdentity(@Path("slug") slug: String, @Body body: IdentityUpdateDto): Response<ResponseBody>

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
    suspend fun partChannel(@Path("slug") slug: String, @Path("channel") channel: String): Response<ResponseBody>
}
