package pm.antani.resentin.domain.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import pm.antani.resentin.R
import pm.antani.resentin.data.db.AppDatabase
import pm.antani.resentin.data.prefs.AppPreferences
import pm.antani.resentin.net.HttpClients
import pm.antani.resentin.net.auth.TokenStore
import pm.antani.resentin.net.dto.AuthLoginRequestDto
import pm.antani.resentin.net.dto.ConfigDto
import pm.antani.resentin.net.dto.MeDto
import pm.antani.resentin.net.rest.AuthApi
import pm.antani.resentin.net.rest.ConfigApi
import pm.antani.resentin.net.rest.MeApi

class AuthRepository(
    private val tokenStore: TokenStore,
    private val db: AppDatabase,
    private val appPreferences: AppPreferences,
    private val context: Context,
) {

    val session: StateFlow<TokenStore.Session?> = tokenStore.session

    suspend fun fetchServerConfig(host: String): Result<ConfigDto> = runCatching {
        val retrofit = HttpClients.retrofit(host, HttpClients.okHttpClient())
        retrofit.create(ConfigApi::class.java).getConfig()
    }

    /** Exchanges the account's own username+password for a bearer token, exactly like
     * a browser session login. A per-client token skips this entirely (see [verifyToken]) —
     * this is for people who don't already have one minted. TOTP/passkey-armed accounts
     * answer 202 here, which this client doesn't implement; the message tells the user
     * to fall back to a client token instead of failing silently. */
    suspend fun loginWithPassword(host: String, identifier: String, password: String): Result<String> = runCatching {
        val retrofit = HttpClients.retrofit(host, HttpClients.okHttpClient())
        val response = retrofit.create(AuthApi::class.java).login(AuthLoginRequestDto(identifier, password))
        when (response.code()) {
            200 -> checkNotNull(response.body()?.token) { context.getString(R.string.auth_error_invalid_response) }
            202 -> error(context.getString(R.string.auth_error_2fa_required))
            401 -> error(context.getString(R.string.auth_error_invalid_credentials))
            429 -> error(context.getString(R.string.auth_error_too_many_attempts))
            else -> error("HTTP ${response.code()}")
        }
    }

    suspend fun verifyToken(host: String, token: String): Result<String> = runCatching {
        val retrofit = HttpClients.retrofit(host, HttpClients.okHttpClient { token })
        val response = retrofit.create(MeApi::class.java).getMe()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        checkNotNull(response.body()?.displayName) { context.getString(R.string.auth_error_invalid_response) }
    }

    /** Every cached table (networks/channels/messages/members/...) is keyed by network
     * *slug* alone with no host column, so switching to a different grappa server whose
     * network happens to share a slug (e.g. two servers both naming a network "azzurra")
     * would otherwise silently merge their data — wrong read-cursor state, cross-server
     * message bleed, the works. Wiping the cache on a detected host change keeps the
     * single-server-at-a-time schema assumption actually true. */
    suspend fun signIn(host: String, token: String, username: String) {
        val lastHost = appPreferences.lastSyncedHost.first()
        if (lastHost != null && lastHost != host) {
            // Room forbids clearAllTables() on the calling thread — signIn() runs on
            // Dispatchers.Main.immediate via LoginViewModel's viewModelScope.launch, so
            // without this it throws IllegalStateException("Cannot access database on
            // the main thread") and crashes outright the first time anyone actually
            // signs into a second, different host on the same install.
            withContext(Dispatchers.IO) { db.clearAllTables() }
        }
        appPreferences.setLastSyncedHost(host)
        tokenStore.saveSession(host, token, username)
    }

    suspend fun getMe(): Result<MeDto> = runCatching {
        val response = api(MeApi::class.java).getMe()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        checkNotNull(response.body())
    }

    fun signOut() {
        tokenStore.clear()
    }

    fun <T> api(serviceClass: Class<T>): T {
        val currentSession = checkNotNull(tokenStore.session.value) { context.getString(R.string.auth_error_not_authenticated) }
        val retrofit = HttpClients.retrofit(currentSession.host, HttpClients.okHttpClient { currentSession.token })
        return retrofit.create(serviceClass)
    }
}
