package pm.antani.resentin.net.rest

import okhttp3.ResponseBody
import pm.antani.resentin.net.dto.E2eAcceptDto
import pm.antani.resentin.net.dto.E2eAckDto
import pm.antani.resentin.net.dto.E2eAskDto
import pm.antani.resentin.net.dto.E2eAskedDto
import pm.antani.resentin.net.dto.E2eRequestsDto
import pm.antani.resentin.net.dto.E2eDeviceDto
import pm.antani.resentin.net.dto.E2eInboxDto
import pm.antani.resentin.net.dto.E2eSendDto
import pm.antani.resentin.net.dto.E2eSentDto
import pm.antani.resentin.net.dto.E2eDevicesDto
import pm.antani.resentin.net.dto.E2eKeysStatusDto
import pm.antani.resentin.net.dto.E2eKeysUploadDto
import pm.antani.resentin.net.dto.E2eListDto
import pm.antani.resentin.net.dto.E2eRegisterDeviceDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Per-device E2E doors (CLIENT_PROTOCOL §4f, protocol >= 49). A `did` is base64url and goes in the path raw. */
interface E2eApi {
    @POST("networks/{slug}/e2e/devices")
    suspend fun registerDevice(@Path("slug") slug: String, @Body body: E2eRegisterDeviceDto): E2eDeviceDto

    @GET("networks/{slug}/e2e/devices")
    suspend fun listDevices(@Path("slug") slug: String): E2eDevicesDto

    @DELETE("networks/{slug}/e2e/devices/{did}")
    suspend fun removeDevice(@Path("slug") slug: String, @Path("did", encoded = true) did: String): Response<ResponseBody>

    /** 404 means no list, or a network that does not resolve for this subject. */
    @GET("networks/{slug}/e2e/list")
    suspend fun getList(@Path("slug") slug: String): Response<E2eListDto>

    @PUT("networks/{slug}/e2e/list")
    suspend fun putList(@Path("slug") slug: String, @Body body: E2eListDto): E2eListDto

    @GET("networks/{slug}/e2e/devices/{did}/keys")
    suspend fun getKeys(@Path("slug") slug: String, @Path("did", encoded = true) did: String): E2eKeysStatusDto

    @POST("networks/{slug}/e2e/devices/{did}/keys")
    suspend fun uploadKeys(
        @Path("slug") slug: String,
        @Path("did", encoded = true) did: String,
        @Body body: E2eKeysUploadDto,
    ): E2eKeysStatusDto

    @GET("networks/{slug}/e2e/devices/{did}/inbox")
    suspend fun inbox(
        @Path("slug") slug: String,
        @Path("did", encoded = true) did: String,
        @Query("after") after: Long,
    ): E2eInboxDto

    @POST("networks/{slug}/e2e/devices/{did}/inbox/ack")
    suspend fun ack(
        @Path("slug") slug: String,
        @Path("did", encoded = true) did: String,
        @Body body: E2eAckDto,
    ): Response<ResponseBody>

    /** 202: accepted, not delivered. grappa frames, paces and names the message. */
    @POST("networks/{slug}/e2e/messages")
    suspend fun send(@Path("slug") slug: String, @Body body: E2eSendDto): E2eSentDto

    @GET("networks/{slug}/e2e/requests")
    suspend fun requests(@Path("slug") slug: String): E2eRequestsDto

    /** 202. REQUEST goes to the peer; when theirs is waiting, ACCEPT goes instead (a crossing). */
    @POST("networks/{slug}/e2e/requests")
    suspend fun ask(@Path("slug") slug: String, @Body body: E2eAskDto): E2eAskedDto

    /** 202. ACCEPT goes to the peer and the request goes. Needs a listed device. */
    @POST("networks/{slug}/e2e/requests/{id}/accept")
    suspend fun acceptRequest(@Path("slug") slug: String, @Path("id") id: Long, @Body body: E2eAcceptDto): E2eSentDto

    /** 202. Without a body grappa sends REFUSE itself: saying no needs no encryption identity. */
    @POST("networks/{slug}/e2e/requests/{id}/refuse")
    suspend fun refuseRequest(@Path("slug") slug: String, @Path("id") id: Long): E2eSentDto

    /** Dismisses an answer, or an expired request (no body needed for those). */
    @DELETE("networks/{slug}/e2e/requests/{id}")
    suspend fun dismissRequest(@Path("slug") slug: String, @Path("id") id: Long): Response<ResponseBody>

    /** Drops the whole identity: every device, its keys and queue, and the list. */
    @DELETE("networks/{slug}/e2e/keys")
    suspend fun clearKeys(@Path("slug") slug: String): Response<ResponseBody>
}
