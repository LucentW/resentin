package pm.antani.resentin.net.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TokenStore(context: Context) {

    data class Session(val host: String, val token: String, val username: String)

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
        return Session(host, token, username)
    }

    fun saveSession(host: String, token: String, username: String) {
        prefs.edit()
            .putString(KEY_HOST, host)
            .putString(KEY_TOKEN, token)
            .putString(KEY_USERNAME, username)
            .apply()
        _session.value = Session(host, token, username)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _session.value = null
    }

    private companion object {
        const val KEY_HOST = "server_host"
        const val KEY_TOKEN = "client_token"
        const val KEY_USERNAME = "username"
    }
}
