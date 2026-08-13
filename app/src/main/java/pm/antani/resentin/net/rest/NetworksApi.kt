package pm.antani.resentin.net.rest

import okhttp3.ResponseBody
import pm.antani.resentin.net.dto.ArchiveEnvelopeDto
import pm.antani.resentin.net.dto.ChannelDto
import pm.antani.resentin.net.dto.DirectoryPageDto
import pm.antani.resentin.net.dto.JoinChannelRequestDto
import pm.antani.resentin.net.dto.NetworkDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NetworksApi {
    @GET("networks")
    suspend fun getNetworks(): List<NetworkDto>

    @GET("networks/{slug}/channels")
    suspend fun getChannels(@Path("slug") slug: String): List<ChannelDto>

    /** JOIN — accepts a single channel or an RFC1459 comma-separated list (server-side,
     * #382); this client always sends one. Returns 202 + `{"ok": true}` once the JOIN
     * frame is queued, not once it lands — the channel shows up as `:pending` in the
     * next `GET .../channels` and is confirmed live over the already-joined WS topic. */
    @POST("networks/{slug}/channels")
    suspend fun joinChannel(@Path("slug") slug: String, @Body body: JoinChannelRequestDto): Response<ResponseBody>

    @GET("networks/{slug}/directory")
    suspend fun getDirectory(
        @Path("slug") slug: String,
        @Query("sort") sort: String,
        @Query("q") q: String? = null,
        @Query("cursor") cursor: String? = null,
    ): DirectoryPageDto

    /** Arms a fresh upstream LIST snapshot; both a started refresh and an already-running
     * one answer 202. The actual page only lands via a subsequent [getDirectory] poll —
     * there is no server push for directory-refresh completion. */
    @POST("networks/{slug}/directory/refresh")
    suspend fun refreshDirectory(@Path("slug") slug: String): Response<ResponseBody>

    @GET("networks/{slug}/archive")
    suspend fun getArchive(@Path("slug") slug: String): ArchiveEnvelopeDto

    /** Drops the LOCAL scrollback for one archived target — the IRC server itself is
     * untouched (a channel target stays rejoinable), see `ArchiveController`. */
    @DELETE("networks/{slug}/archive/{target}")
    suspend fun deleteArchiveEntry(@Path("slug") slug: String, @Path("target") target: String): Response<ResponseBody>
}
