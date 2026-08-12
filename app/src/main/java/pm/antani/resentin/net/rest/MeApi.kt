package pm.antani.resentin.net.rest

import pm.antani.resentin.net.dto.MeDto
import retrofit2.Response
import retrofit2.http.GET

interface MeApi {
    @GET("me")
    suspend fun getMe(): Response<MeDto>
}
