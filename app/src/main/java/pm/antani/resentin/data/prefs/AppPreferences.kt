package pm.antani.resentin.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_prefs")

/** Purely a local rendering choice — never sent to the server, unlike e.g. the
 * server-persisted `coloredNicklist` display pref. */
enum class ChatDisplayMode {
    BUBBLES,
    IRC_LINE,
}

class AppPreferences(private val context: Context) {

    private val keyStayConnected = booleanPreferencesKey("stay_connected")
    private val keyChatDisplayMode = stringPreferencesKey("chat_display_mode")
    private val keyShowSeconds = booleanPreferencesKey("show_seconds")

    val stayConnected: Flow<Boolean> = context.dataStore.data.map { it[keyStayConnected] ?: false }

    suspend fun setStayConnected(value: Boolean) {
        context.dataStore.edit { it[keyStayConnected] = value }
    }

    val chatDisplayMode: Flow<ChatDisplayMode> = context.dataStore.data.map {
        if (it[keyChatDisplayMode] == ChatDisplayMode.IRC_LINE.name) ChatDisplayMode.IRC_LINE else ChatDisplayMode.BUBBLES
    }

    suspend fun setChatDisplayMode(mode: ChatDisplayMode) {
        context.dataStore.edit { it[keyChatDisplayMode] = mode.name }
    }

    /** Applies to the timestamp shown on every message row, in both display modes. */
    val showSeconds: Flow<Boolean> = context.dataStore.data.map { it[keyShowSeconds] ?: false }

    suspend fun setShowSeconds(value: Boolean) {
        context.dataStore.edit { it[keyShowSeconds] = value }
    }
}
