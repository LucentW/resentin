package pm.antani.resentin.domain.session

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hand-off point for the Android share-target flow: MainActivity receives an
 * ACTION_SEND/SEND_MULTIPLE intent (before we know which chat it's destined for), stages
 * the shared URIs here, and the "pick a chat" screen navigates into a ChatScreen once the
 * user chooses one. That ChatViewModel then [consume]s the URIs and uploads them — a
 * StateFlow rather than passing URIs through Compose navigation args, which don't
 * support arbitrary lists cleanly.
 */
class PendingShareHolder {
    private val _uris = MutableStateFlow<List<Uri>>(emptyList())
    val uris: StateFlow<List<Uri>> = _uris.asStateFlow()

    fun set(uris: List<Uri>) {
        _uris.value = uris
    }

    /** One-shot read: returns the staged URIs and clears them, so a second ChatViewModel
     * (e.g. re-opening the same chat later) never re-uploads the same share. */
    fun consume(): List<Uri> {
        val current = _uris.value
        _uris.value = emptyList()
        return current
    }
}
