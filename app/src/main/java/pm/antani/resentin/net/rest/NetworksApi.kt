package pm.antani.resentin.net.rest

import okhttp3.ResponseBody
import pm.antani.resentin.net.dto.ArchiveEnvelopeDto
import pm.antani.resentin.net.dto.ChannelDto
import pm.antani.resentin.net.dto.DirectoryPageDto
import pm.antani.resentin.net.dto.FeaturedChannelsResponseDto
import pm.antani.resentin.net.dto.JoinChannelRequestDto
import pm.antani.resentin.net.dto.NetworkDto
import pm.antani.resentin.net.dto.SessionNetworkRequestDto
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

    /** Attach and start a self-serve network offered by `GET /me` home_data. */
    @POST("session/networks")
    suspend fun addSessionNetwork(@Body body: SessionNetworkRequestDto): Response<ResponseBody>

    @GET("networks/{slug}/channels")
    suspend fun getChannels(@Path("slug") slug: String): List<ChannelDto>

    /** JOIN — accepts a single channel or an RFC1459 comma-separated list (server-side,
     * #382); this client always sends one. Returns 202 + `{"ok": true}` once the JOIN
     * frame is queued, not once it lands — the channel shows up as `:pending` in the
     * next `GET .../channels` and is confirmed live over the already-joined WS topic. */
    @POST("networks/{slug}/channels")
    suspend fun joinChannel(@Path("slug") slug: String, @Body body: JoinChannelRequestDto): Response<ResponseBody>

    /** Refuses an inbound INVITE (#976 on grappa-irc): drops the local `:invited`
     * window and fans `window_invite_declined` out to every device on the account.
     * Nothing is sent upstream — IRC has no DECLINE verb. */
    @DELETE("networks/{slug}/invites/{channel}")
    suspend fun declineInvite(@Path("slug") slug: String, @Path("channel") channel: String): Response<ResponseBody>

    /** Consents to a held DCC offer (issue 2089 on grappa-irc). 202: the admission is
     * complete (quota spent, offer left the held set) but the transfer runs detached —
     * the outcome lands later as a scrollback row, not in this response. */
    @POST("networks/{slug}/dcc_offers/{offerId}/accept")
    suspend fun acceptDccOffer(@Path("slug") slug: String, @Path("offerId") offerId: String): Response<ResponseBody>

    /** Refuses a held DCC offer. Nothing is sent to the peer — IRC has no DCC REJECT
     * this bouncer relays; this only drops the local hold and its banner everywhere. */
    @DELETE("networks/{slug}/dcc_offers/{offerId}")
    suspend fun declineDccOffer(@Path("slug") slug: String, @Path("offerId") offerId: String): Response<ResponseBody>

    @GET("networks/{slug}/directory")
    suspend fun getDirectory(
        @Path("slug") slug: String,
        @Query("sort") sort: String,
        @Query("q") q: String? = null,
        @Query("cursor") cursor: String? = null,
    ): DirectoryPageDto

    /** Arms a fresh upstream LIST snapshot; both a started refresh and an already-running
     * one answer 202. The server pushes directory_progress/directory_complete (or
     * directory_failed) on the WebSocket; clients fetch page one once after completion. */
    @GET("networks/{slug}/featured")
    suspend fun getFeaturedChannels(@Path("slug") slug: String): FeaturedChannelsResponseDto

    @POST("networks/{slug}/directory/refresh")
    suspend fun refreshDirectory(@Path("slug") slug: String): Response<ResponseBody>

    @GET("networks/{slug}/archive")
    suspend fun getArchive(@Path("slug") slug: String): ArchiveEnvelopeDto

    /** Drops the LOCAL scrollback for one archived target — the IRC server itself is
     * untouched (a channel target stays rejoinable), see `ArchiveController`. */
    @DELETE("networks/{slug}/archive/{target}")
    suspend fun deleteArchiveEntry(@Path("slug") slug: String, @Path("target") target: String): Response<ResponseBody>
}
