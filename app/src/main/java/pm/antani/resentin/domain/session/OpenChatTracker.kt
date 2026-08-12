package pm.antani.resentin.domain.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OpenChat(val networkSlug: String, val channelName: String)

/** Tracks which chat screen (if any) is currently foregrounded, so the
 * notification router can suppress a notification for a chat the user is
 * already looking at. */
class OpenChatTracker {
    private val _current = MutableStateFlow<OpenChat?>(null)
    val current: StateFlow<OpenChat?> = _current.asStateFlow()

    fun onChatOpened(networkSlug: String, channelName: String) {
        _current.value = OpenChat(networkSlug, channelName)
    }

    fun onChatClosed(networkSlug: String, channelName: String) {
        if (_current.value == OpenChat(networkSlug, channelName)) {
            _current.value = null
        }
    }
}
