package pm.antani.resentin.domain.repository

import kotlinx.coroutines.flow.first
import org.unifiedpush.android.connector.data.PushEndpoint
import pm.antani.resentin.data.prefs.AppPreferences
import pm.antani.resentin.net.dto.PushKeysDto
import pm.antani.resentin.net.dto.PushSubscriptionRequestDto
import pm.antani.resentin.net.dto.PushSubscriptionSummaryDto
import pm.antani.resentin.net.rest.PushApi

/** Bridges the UnifiedPush connector library (device-local registration with a
 * distributor) and grappa's `/push/subscriptions` REST surface (server-side delivery
 * targets), which treats a UnifiedPush endpoint as just another Web Push subscription —
 * see the `unifiedpush-support` branch of grappa-irc, PR #1261. */
class PushRepository(
    private val authRepository: AuthRepository,
    private val appPreferences: AppPreferences,
) {

    suspend fun fetchVapidPublicKey(): Result<String> = runCatching {
        authRepository.api(PushApi::class.java).getVapidPublicKey().publicKey
    }

    /** Registers a freshly (re-)issued distributor endpoint with the server. Chains a
     * `supersedes` on the endpoint this device last registered, if any, so token
     * rotation or a distributor reinstall doesn't accumulate ghost rows for the same
     * device (mirrors cic's own re-subscribe dedup, #181). */
    suspend fun registerEndpoint(endpoint: PushEndpoint): Result<Unit> = runCatching {
        val keys = checkNotNull(endpoint.pubKeySet) { "distributor endpoint carries no Web Push key set" }
        val previousEndpoint = appPreferences.unifiedPushEndpoint.first()
        val api = authRepository.api(PushApi::class.java)
        val response = api.subscribe(
            PushSubscriptionRequestDto(
                endpoint = endpoint.url,
                keys = PushKeysDto(p256dh = keys.pubKey, auth = keys.auth),
                supersedes = previousEndpoint?.takeIf { it != endpoint.url },
            ),
        )
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        val body = checkNotNull(response.body())
        appPreferences.setUnifiedPushEndpoint(endpoint.url)
        appPreferences.setUnifiedPushSubscriptionId(body.id)
    }

    suspend fun listSubscriptions(): Result<List<PushSubscriptionSummaryDto>> = runCatching {
        authRepository.api(PushApi::class.java).listSubscriptions().subscriptions
    }

    suspend fun deleteSubscription(id: String): Result<Unit> = runCatching {
        val response = authRepository.api(PushApi::class.java).deleteSubscription(id)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        if (appPreferences.unifiedPushSubscriptionId.first() == id) {
            clearLocalRegistration()
        }
    }

    /** Deletes this device's own server-side subscription (if any) and forgets the
     * local registration bookkeeping — used both when the user explicitly disables
     * push in Settings and when a distributor reports [onUnregistered]. */
    suspend fun deleteOwnSubscription(): Result<Unit> = runCatching {
        val id = appPreferences.unifiedPushSubscriptionId.first() ?: return@runCatching
        val response = authRepository.api(PushApi::class.java).deleteSubscription(id)
        check(response.isSuccessful || response.code() == 404) { "HTTP ${response.code()}" }
        clearLocalRegistration()
    }

    suspend fun clearLocalRegistration() {
        appPreferences.setUnifiedPushEndpoint(null)
        appPreferences.setUnifiedPushSubscriptionId(null)
    }
}
