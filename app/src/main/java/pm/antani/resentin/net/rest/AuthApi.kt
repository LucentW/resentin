package pm.antani.resentin.net.rest

import okhttp3.ResponseBody
import pm.antani.resentin.net.dto.AuthLoginRequestDto
import pm.antani.resentin.net.dto.AuthLoginResponseDto
import pm.antani.resentin.net.dto.ShareConsumeRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: AuthLoginRequestDto): Response<AuthLoginResponseDto>

    /** "Detach" (cicchetto parity) — revokes THIS web session; a persistent identity's
     * upstream IRC connection stays up server-side (bouncer-style). Only an ephemeral/
     * anon visitor gets a full teardown from this alone — see AuthRepository.detach. */
    @DELETE("auth/logout")
    suspend fun logout(): Response<ResponseBody>

    /** Session-sharing redeem — unauthenticated, the token itself is the credential
     * (cicchetto's QR/link "open on another device"). See AuthRepository.consumeShareToken. */
    @POST("auth/share/consume")
    suspend fun consumeShareToken(@Body body: ShareConsumeRequestDto): Response<AuthLoginResponseDto>
}
