package pm.antani.resentin.net.rest

import pm.antani.resentin.net.dto.ReadCursorRequestDto
import pm.antani.resentin.net.dto.ReadCursorResponseDto
import pm.antani.resentin.net.dto.ScrollbackMessageDto
import pm.antani.resentin.net.dto.SendMessageDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MessagesApi {
    @POST("networks/{slug}/channels/{channel}/messages")
    suspend fun sendMessage(
        @Path("slug") slug: String,
        @Path("channel") channel: String,
        @Body body: SendMessageDto,
    ): Response<ScrollbackMessageDto>

    @GET("networks/{slug}/channels/{channel}/messages")
    suspend fun getMessages(
        @Path("slug") slug: String,
        @Path("channel") channel: String,
        @Query("after") after: Long? = null,
        @Query("before") before: Long? = null,
        @Query("limit") limit: Int? = null,
    ): List<ScrollbackMessageDto>

    /** Monotonic advance-only on the server — safe to call with any id, even a stale one. */
    @POST("networks/{slug}/channels/{channel}/read-cursor")
    suspend fun setReadCursor(
        @Path("slug") slug: String,
        @Path("channel") channel: String,
        @Body body: ReadCursorRequestDto,
    ): Response<ReadCursorResponseDto>
}
