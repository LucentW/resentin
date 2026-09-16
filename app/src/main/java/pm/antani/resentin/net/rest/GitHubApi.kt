package pm.antani.resentin.net.rest

import pm.antani.resentin.net.dto.GitHubReleaseDto
import retrofit2.http.GET

interface GitHubApi {
    @GET("repos/LucentW/resentin/releases/latest")
    suspend fun latestRelease(): GitHubReleaseDto
}
