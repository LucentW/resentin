package pm.antani.resentin.net.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TokenStore(context: Context) {

    data class Session(val host: String, val token: String, val username: String, val wsSubject: String)

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
        return Session(host, token, username, wsSubject)
    }

    fun saveSession(host: String, token: String, username: String, wsSubject: String) {
        prefs.edit()
            .putString(KEY_HOST, host)
            .putString(KEY_TOKEN, token)
            .putString(KEY_USERNAME, username)
            .putString(KEY_WS_SUBJECT, wsSubject)
            .apply()
        _session.value = Session(host, token, username, wsSubject)
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
    }
}
