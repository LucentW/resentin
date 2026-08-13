package pm.antani.resentin.net.rest

import pm.antani.resentin.net.dto.PushSubscriptionCreatedDto
import pm.antani.resentin.net.dto.PushSubscriptionRequestDto
import pm.antani.resentin.net.dto.PushSubscriptionsEnvelopeDto
import pm.antani.resentin.net.dto.PushVapidKeyDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface PushApi {
    @GET("push/vapid-public-key")
    suspend fun getVapidPublicKey(): PushVapidKeyDto

    @POST("push/subscriptions")
    suspend fun subscribe(@Body body: PushSubscriptionRequestDto): Response<PushSubscriptionCreatedDto>

    @GET("push/subscriptions")
    suspend fun listSubscriptions(): PushSubscriptionsEnvelopeDto

    @DELETE("push/subscriptions/{id}")
    suspend fun deleteSubscription(@Path("id") id: String): Response<Unit>
}
