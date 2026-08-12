package pm.antani.resentin.net.rest

import pm.antani.resentin.net.dto.ConfigDto
import retrofit2.http.GET

interface ConfigApi {
    @GET("api/config")
    suspend fun getConfig(): ConfigDto
}
