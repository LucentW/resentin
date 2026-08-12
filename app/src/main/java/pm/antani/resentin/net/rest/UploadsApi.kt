package pm.antani.resentin.net.rest

import okhttp3.MultipartBody
import pm.antani.resentin.net.dto.UploadResponseDto
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface UploadsApi {
    /** `/api` prefix (unlike every other endpoint this app calls) — that's the server's
     * actual route, not a typo; see grappa's router.ex UX-6-B1 upload cluster. */
    @Multipart
    @POST("api/uploads")
    suspend fun upload(@Part file: MultipartBody.Part): Response<UploadResponseDto>
}
