package pm.antani.resentin.net.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TokenStore(context: Context) {

    /** [kind] is `/me`'s discriminator verbatim (`"user"` | `"visitor"`) — decides
     * whether sign-out offers the detach/quit choice (a registered user) or just
     * detaches outright (a visitor — see AuthRepository.detach's moduledoc). */
    data class Session(val host: String, val token: String, val username: String, val wsSubject: String, val kind: String) {
        val isVisitor: Boolean get() = kind == "visitor"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "resentin_auth",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _session = MutableStateFlow(readSession())
    val session: StateFlow<Session?> = _session

    private fun readSession(): Session? {
        val host = prefs.getString(KEY_HOST, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        // Falls back to `username` for a session saved before wsSubject existed — only
        // wrong for a pre-existing VISITOR session (a user's subject already equals its
        // username), and visitor sessions are short-lived enough that a fresh login is
        // an acceptable one-time cost.
        val wsSubject = prefs.getString(KEY_WS_SUBJECT, null) ?: username
        // Falls back by sniffing the subject prefix for a session saved before `kind`
        // existed — every session minted since the wsSubject fix carries an explicit
        // "visitor:" prefix for visitors and nothing else does.
        val kind = prefs.getString(KEY_KIND, null) ?: if (wsSubject.startsWith("visitor:")) "visitor" else "user"
        return Session(host, token, username, wsSubject, kind)
    }

    fun saveSession(host: String, token: String, username: String, wsSubject: String, kind: String) {
        prefs.edit()
            .putString(KEY_HOST, host)
            .putString(KEY_TOKEN, token)
            .putString(KEY_USERNAME, username)
            .putString(KEY_WS_SUBJECT, wsSubject)
            .putString(KEY_KIND, kind)
            .apply()
        _session.value = Session(host, token, username, wsSubject, kind)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _session.value = null
    }

    private companion object {
        const val KEY_HOST = "server_host"
        const val KEY_TOKEN = "client_token"
        const val KEY_USERNAME = "username"
        const val KEY_WS_SUBJECT = "ws_subject"
        const val KEY_KIND = "kind"
    }
}
