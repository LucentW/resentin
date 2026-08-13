package pm.antani.resentin.service

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import pm.antani.resentin.AppApplication
import pm.antani.resentin.net.AppJson
import pm.antani.resentin.net.dto.PushNotificationPayloadDto

/** Receives distributor callbacks for the battery-friendly, foreground-optional push
 * path — see the `unifiedpush-notifications` branch design discussion and grappa-irc PR
 * #1261. Registered as a `<service>` (not a `BroadcastReceiver`); see AndroidManifest.xml. */
class UnifiedPushService : PushService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        Log.d(TAG, "onNewEndpoint instance=$instance")
        val container = (application as AppApplication).container
        scope.launch {
            container.pushRepository.registerEndpoint(endpoint)
                .onFailure { Log.w(TAG, "failed to register UnifiedPush endpoint with server", it) }
        }
    }

    /** [message.content] arrives ALREADY DECRYPTED — the connector library owns the full
     * RFC8291 Web Push crypto (ECDH + HKDF + aes128gcm) using the keypair it generated
     * and handed to the server in [onNewEndpoint]; nothing here does any decryption. */
    override fun onMessage(message: PushMessage, instance: String) {
        if (!message.decrypted) {
            Log.w(TAG, "received a push message that failed decryption, dropping")
            return
        }
        val payload = runCatching {
            AppJson.decodeFromString(PushNotificationPayloadDto.serializer(), message.content.toString(Charsets.UTF_8))
        }.getOrElse {
            Log.w(TAG, "could not parse push payload", it)
            return
        }
        // Mirrors Grappa.Push.Payload.build_url/2: "/?network=<slug>&channel=<percent-encoded>".
        val uri = Uri.parse(payload.url)
        val network = uri.getQueryParameter("network")
        val channel = uri.getQueryParameter("channel")
        if (network == null || channel == null) {
            Log.w(TAG, "push payload url missing network/channel: ${payload.url}")
            return
        }
        val container = (application as AppApplication).container
        scope.launch {
            runCatching { container.notificationRouter.notifyFromPush(network, channel) }
                .onFailure { Log.w(TAG, "failed to handle push wake-up for $network/$channel", it) }
        }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        // Every registration this client issues already carries a VAPID key (see
        // AppContainer's app-start re-registration and AppSettingsScreen's enable flow),
        // so a distributor still asking for one (FailedReason.VAPID_REQUIRED) would only
        // happen against a stale in-flight request; nothing to recover here beyond
        // logging — the next re-registration attempt retries with the key attached.
        Log.w(TAG, "UnifiedPush registration failed: $reason")
    }

    override fun onUnregistered(instance: String) {
        Log.d(TAG, "UnifiedPush unregistered by distributor, instance=$instance")
        val container = (application as AppApplication).container
        scope.launch {
            container.pushRepository.deleteOwnSubscription()
                .onFailure { Log.w(TAG, "failed to delete server-side subscription after distributor unregister", it) }
            container.appPreferences.setUnifiedPushEnabled(false)
        }
    }

    companion object {
        private const val TAG = "UnifiedPushService"
    }
}
