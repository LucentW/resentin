package pm.antani.resentin.net.rest

import pm.antani.resentin.net.dto.AuthLoginRequestDto
import pm.antani.resentin.net.dto.AuthLoginResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: AuthLoginRequestDto): Response<AuthLoginResponseDto>
}
