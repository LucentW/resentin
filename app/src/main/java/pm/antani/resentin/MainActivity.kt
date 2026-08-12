package pm.antani.resentin

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import pm.antani.resentin.service.NotificationRouter
import pm.antani.resentin.ui.AppRoot
import pm.antani.resentin.ui.DeepLinkChat
import pm.antani.resentin.ui.theme.ResentinTheme

class MainActivity : ComponentActivity() {

    private var pendingDeepLink = mutableStateOf<DeepLinkChat?>(null)
    private var pendingSharePick = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AppApplication).container
        handleIntent(intent)
        setContent {
            ResentinTheme {
                AppRoot(
                    container = container,
                    deepLink = pendingDeepLink.value,
                    onDeepLinkConsumed = { pendingDeepLink.value = null },
                    sharePick = pendingSharePick.value,
                    onSharePickConsumed = { pendingSharePick.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        deepLinkFrom(intent)?.let { pendingDeepLink.value = it }
        val sharedUris = shareUrisFrom(intent)
        if (sharedUris.isNotEmpty()) {
            (application as AppApplication).container.pendingShareHolder.set(sharedUris)
            pendingSharePick.value = true
        }
    }

    private fun deepLinkFrom(intent: Intent?): DeepLinkChat? {
        val networkSlug = intent?.getStringExtra(NotificationRouter.EXTRA_NETWORK_SLUG) ?: return null
        val channelName = intent.getStringExtra(NotificationRouter.EXTRA_CHANNEL_NAME) ?: return null
        return DeepLinkChat(networkSlug, channelName)
    }

    /** The Android share sheet — another app's "Condividi" → Resentin. */
    private fun shareUrisFrom(intent: Intent?): List<Uri> = when (intent?.action) {
        Intent.ACTION_SEND -> listOfNotNull(intent.parcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE -> intent.parcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }

    @Suppress("DEPRECATION")
    private fun <T : android.os.Parcelable> Intent.parcelableExtra(name: String, clazz: Class<T>): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(name, clazz) else getParcelableExtra(name)

    @Suppress("DEPRECATION")
    private fun <T : android.os.Parcelable> Intent.parcelableArrayListExtra(name: String, clazz: Class<T>): ArrayList<T>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableArrayListExtra(name, clazz) else getParcelableArrayListExtra(name)
}
