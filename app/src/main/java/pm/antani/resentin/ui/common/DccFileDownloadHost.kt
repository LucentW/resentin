package pm.antani.resentin.ui.common

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import pm.antani.resentin.R
import pm.antani.resentin.domain.repository.AuthRepository

/** Wires [LocalDccFileDownloadHandler] to a real Storage-Access-Framework save flow for
 * the app's whole composition — one instance, mounted once at the root (see
 * `MainActivity`), rather than per-chat: the download path a DCC report embeds
 * (`/networks/{id}/dcc_files/{slug}`) already names its own network, so nothing about
 * this needs to know which chat the tap happened in.
 *
 * `application/octet-stream` matches the server's own Content-Type for these bytes
 * (see `Grappa.DccFilesController`'s moduledoc on why it never sniffs a type). */
@Composable
fun DccFileDownloadHost(authRepository: AuthRepository, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pendingPath = remember { mutableStateOf<String?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val path = pendingPath.value
        pendingPath.value = null
        if (uri == null || path == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = authRepository.fetchBytes(path)
            val saved = bytes != null && runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }.isSuccess
            Toast.makeText(
                context,
                context.getString(if (saved) R.string.dcc_download_success else R.string.dcc_download_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    CompositionLocalProvider(
        LocalDccFileDownloadHandler provides { path, filename ->
            pendingPath.value = path
            saveLauncher.launch(filename?.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/'))
        },
    ) {
        content()
    }
}
