package pm.antani.resentin

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import pm.antani.resentin.data.prefs.MessageDensity
import pm.antani.resentin.data.prefs.AppFontFamily
import pm.antani.resentin.data.prefs.ThemeMode
import pm.antani.resentin.service.NotificationRouter
import pm.antani.resentin.ui.AppRoot
import pm.antani.resentin.ui.DeepLinkChat
import pm.antani.resentin.ui.common.LocalDensityScale
import pm.antani.resentin.ui.login.parseGrappaLoginLink
import pm.antani.resentin.ui.theme.ResentinTheme

class MainActivity : ComponentActivity() {

    private var pendingDeepLink = mutableStateOf<DeepLinkChat?>(null)
    private var pendingSharePick = mutableStateOf(false)
    private var pendingLoginLink = mutableStateOf<Pair<String, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AppApplication).container
        handleIntent(intent)
        setContent {
            // Global text size (Settings slider) — read here so the whole theme,
            // every screen included, rescales live with it. Same for the forced
            // theme override (Settings > Aspetto): SYSTEM keeps following the OS.
            val fontScale by container.appPreferences.fontScale.collectAsState(initial = 1f)
            val fontFamily by container.appPreferences.fontFamily.collectAsState(initial = AppFontFamily.SYSTEM)
            val chatFontFamily by container.appPreferences.chatFontFamily.collectAsState(initial = AppFontFamily.SYSTEM)
            val lineHeightScale by container.appPreferences.lineHeightScale.collectAsState(initial = 1f)
            val messageDensity by container.appPreferences.messageDensity.collectAsState(initial = MessageDensity.NORMAL)
            val themeMode by container.appPreferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val useDarkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            ResentinTheme(
                darkTheme = useDarkTheme,
                fontScale = fontScale,
                lineHeightScale = lineHeightScale,
                fontFamilyChoice = fontFamily,
                chatFontFamilyChoice = chatFontFamily,
            ) {
                CompositionLocalProvider(LocalDensityScale provides messageDensity.scale) {
                AppRoot(
                    container = container,
                    deepLink = pendingDeepLink.value,
                    onDeepLinkConsumed = { pendingDeepLink.value = null },
                    sharePick = pendingSharePick.value,
                    onSharePickConsumed = { pendingSharePick.value = false },
                    loginLink = pendingLoginLink.value,
                    onLoginLinkConsumed = { pendingLoginLink.value = null },
                )
                }
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
        loginLinkFrom(intent)?.let { pendingLoginLink.value = it }
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

    /** `grappa://<host>/<token>` magic-link login — a QR code / shared link from
     * grappa-irc's own generator (for TOTP/passkey-gated accounts). Ignored entirely
     * when a session already exists: "open the app" is the whole of the contract then,
     * not a re-login or an account switch the user never asked for. */
    private fun loginLinkFrom(intent: Intent?): Pair<String, String>? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        if ((application as AppApplication).container.tokenStore.session.value != null) return null
        return parseGrappaLoginLink(data.toString())
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
