package pm.antani.resentin.net.rest

import okhttp3.ResponseBody
import pm.antani.resentin.net.dto.PairingMessageResponseDto
import pm.antani.resentin.net.dto.PairingMessagesDto
import pm.antani.resentin.net.dto.PairingOfferResponseDto
import pm.antani.resentin.net.dto.PairingOpenDto
import pm.antani.resentin.net.dto.PairingPostDto
import pm.antani.resentin.net.dto.PairingRendezvousResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Pairing two devices of one account (CLIENT_PROTOCOL §4g, v48/v55). Account-level, not per network. */
interface E2ePairingApi {
    /** With `curve` the rendezvous is OFFERED to the account's other devices. */
    @POST("e2e/pairings")
    suspend fun open(@Body body: PairingOpenDto): PairingRendezvousResponseDto

    @GET("e2e/pairings/offer")
    suspend fun offer(): PairingOfferResponseDto

    @POST("e2e/pairings/{id}/join")
    suspend fun join(@Path("id") id: String): PairingRendezvousResponseDto

    @POST("e2e/pairings/{id}/messages")
    suspend fun post(@Path("id") id: String, @Body body: PairingPostDto): PairingMessageResponseDto

    @GET("e2e/pairings/{id}/messages")
    suspend fun messages(@Path("id") id: String, @Query("after") after: Int): Response<PairingMessagesDto>

    @DELETE("e2e/pairings/{id}")
    suspend fun close(@Path("id") id: String): Response<ResponseBody>
}
