package pm.antani.resentin.net.rest

import okhttp3.ResponseBody
import pm.antani.resentin.net.dto.E2eDeviceDto
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

    /** Drops the whole identity: every device, its keys and queue, and the list. */
    @DELETE("networks/{slug}/e2e/keys")
    suspend fun clearKeys(@Path("slug") slug: String): Response<ResponseBody>
}
