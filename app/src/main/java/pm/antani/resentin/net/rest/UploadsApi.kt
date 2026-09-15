package pm.antani.resentin.net.rest

import okhttp3.MultipartBody
import okhttp3.RequestBody
import pm.antani.resentin.net.dto.UploadResponseDto
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface UploadsApi {
    /** `/api` prefix (unlike every other endpoint this app calls) — that's the server's
     * actual route, not a typo; see grappa's router.ex UX-6-B1 upload cluster.
     *
     * [expire]: optional per-upload TTL in seconds — must be on the server's
     * `1h/12h/24h/72h` ladder (3600/43200/86400/259200, see
     * `UPLOAD_TTL_LADDER_SECONDS`) or the server answers 400. `null` parts
     * are omitted by Retrofit, so `null` means "server default" (24h). */
    @Multipart
    @POST("api/uploads")
    suspend fun upload(
        @Part file: MultipartBody.Part,
        @Part("expire") expire: RequestBody? = null,
    ): Response<UploadResponseDto>
}
