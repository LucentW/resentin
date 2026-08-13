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
    private val keyLastSyncedHost = stringPreferencesKey("last_synced_host")
    private val keyUnifiedPushEnabled = booleanPreferencesKey("unifiedpush_enabled")
    private val keyUnifiedPushEndpoint = stringPreferencesKey("unifiedpush_endpoint")
    private val keyUnifiedPushSubscriptionId = stringPreferencesKey("unifiedpush_subscription_id")

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

    /** The host every locally-cached table (networks/channels/messages/...) was last
     * populated from — every table is keyed by network *slug* alone, with no host
     * column, so switching to a different grappa server whose network happens to share
     * a slug (e.g. two servers both naming a network "azzurra") would otherwise silently
     * merge their data. Outlives sign-out (unlike [pm.antani.resentin.net.auth.TokenStore],
     * which the login screen needs cleared) so a host change is still detectable across
     * a sign-out/sign-in cycle. See [pm.antani.resentin.domain.repository.AuthRepository.signIn]. */
    val lastSyncedHost: Flow<String?> = context.dataStore.data.map { it[keyLastSyncedHost] }

    suspend fun setLastSyncedHost(host: String) {
        context.dataStore.edit { it[keyLastSyncedHost] = host }
    }

    /** User opt-in for battery-friendly notifications via a UnifiedPush distributor,
     * independent of [stayConnected] (the always-on WS fallback) — the two can both be
     * on at once, e.g. during the rollout of foreground-only WS sync. */
    val unifiedPushEnabled: Flow<Boolean> = context.dataStore.data.map { it[keyUnifiedPushEnabled] ?: false }

    suspend fun setUnifiedPushEnabled(value: Boolean) {
        context.dataStore.edit { it[keyUnifiedPushEnabled] = value }
    }

    /** The endpoint URL last handed to the server, so a distributor-initiated re-issue
     * (token rotation, distributor reinstall) can be sent to `POST /push/subscriptions`
     * as `supersedes`, letting the server prune the stale row atomically instead of
     * accumulating ghost subscriptions for the same device. */
    val unifiedPushEndpoint: Flow<String?> = context.dataStore.data.map { it[keyUnifiedPushEndpoint] }

    suspend fun setUnifiedPushEndpoint(endpoint: String?) {
        context.dataStore.edit {
            if (endpoint != null) it[keyUnifiedPushEndpoint] = endpoint else it.remove(keyUnifiedPushEndpoint)
        }
    }

    /** This device's own subscription id, so the Settings device list can highlight
     * "this device" among every subscription the account owns. */
    val unifiedPushSubscriptionId: Flow<String?> = context.dataStore.data.map { it[keyUnifiedPushSubscriptionId] }

    suspend fun setUnifiedPushSubscriptionId(id: String?) {
        context.dataStore.edit {
            if (id != null) it[keyUnifiedPushSubscriptionId] = id else it.remove(keyUnifiedPushSubscriptionId)
        }
    }
}
