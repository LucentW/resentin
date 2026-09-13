package pm.antani.resentin.net.rest

import okhttp3.ResponseBody
import pm.antani.resentin.net.dto.NotifyListDto
import pm.antani.resentin.net.dto.NotifyRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface NotifyApi {
    @GET("networks/{slug}/notify")
    suspend fun list(@Path("slug") slug: String): NotifyListDto

    @POST("networks/{slug}/notify")
    suspend fun add(@Path("slug") slug: String, @Body body: NotifyRequestDto): Response<ResponseBody>

    @DELETE("networks/{slug}/notify/{nick}")
    suspend fun remove(@Path("slug") slug: String, @Path("nick") nick: String): Response<ResponseBody>
}