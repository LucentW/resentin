package pm.antani.resentin.ui.applock

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import pm.antani.resentin.R
import pm.antani.resentin.ui.theme.ResentinSpacing

/** Whether this device can do a system lock auth at all (biometrics or
 * PIN/pattern/password). Pre-30 the biometric check alone misses
 * PIN-only devices, so the Keyguard state is OR-ed in. */
fun isSystemLockAvailable(context: Context): Boolean {
    val keyguard = context.getSystemService(KeyguardManager::class.java)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        BiometricManager.from(context)
            .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
    } else {
        @Suppress("DEPRECATION")
        BiometricManager.from(context).canAuthenticate() ==
            BiometricManager.BIOMETRIC_SUCCESS ||
            (keyguard?.isDeviceSecure == true)
    }
}

/** One-shot system auth (fingerprint/face or device PIN/pattern/password).
 * [activity] must be a FragmentActivity — BiometricPrompt's requirement. */
fun authenticateWithSystemLock(
    activity: FragmentActivity,
    title: String,
    subtitle: String?,
    onSuccess: () -> Unit,
    onError: (CharSequence?) -> Unit,
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            // User cancel/dismiss is not an error to surface — they can retry
            // with the Sblocca button.
            if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                errorCode == BiometricPrompt.ERROR_CANCELED
            ) {
                return
            }
            onError(errString)
        }
    }
    val prompt = BiometricPrompt(activity, executor, callback)
    val infoBuilder = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setConfirmationRequired(false)
    if (subtitle != null) infoBuilder.setSubtitle(subtitle)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        infoBuilder.setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
    } else {
        // DEVICE_CREDENTIAL via the compat path: no negative button allowed
        // when the device credential is part of the prompt.
        @Suppress("DEPRECATION")
        infoBuilder.setDeviceCredentialAllowed(true)
    }
    prompt.authenticate(infoBuilder.build())
}

/** Full-screen gate shown instead of the app content while locked. Auth is
 * offered automatically on first composition plus on every tap of Sblocca. */
@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val title = stringResource(R.string.app_lock_auth_title)
    val subtitle = stringResource(R.string.app_lock_subtitle)

    fun launchAuth(onFailed: (CharSequence?) -> Unit) {
        val fragmentActivity = activity ?: return
        authenticateWithSystemLock(
            activity = fragmentActivity,
            title = title,
            subtitle = subtitle,
            onSuccess = onUnlocked,
            onError = { onFailed(it) },
        )
    }

    // Auto-prompt once, so returning to the app asks immediately instead of
    // waiting for a tap. A cancelled prompt just leaves the button.
    var autoPrompted by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (!autoPrompted) {
            autoPrompted = true
            launchAuth { err -> errorText = err?.toString() }
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().padding(ResentinSpacing.xLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(ResentinSpacing.large))
            Text(
                stringResource(R.string.app_lock_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(ResentinSpacing.small))
            Text(
                stringResource(R.string.app_lock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (errorText != null) {
                Spacer(Modifier.height(ResentinSpacing.small))
                Text(
                    errorText.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            } else if (activity == null) {
                Spacer(Modifier.height(ResentinSpacing.small))
                Text(
                    stringResource(R.string.app_lock_auth_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(ResentinSpacing.large))
            Button(
                onClick = { launchAuth { err -> errorText = err?.toString() } },
                enabled = activity != null,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(stringResource(R.string.app_lock_unlock))
            }
        }
    }
}
