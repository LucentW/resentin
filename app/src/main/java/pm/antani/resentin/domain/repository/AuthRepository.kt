package pm.antani.resentin.domain.repository

import kotlinx.coroutines.flow.StateFlow
import pm.antani.resentin.net.HttpClients
import pm.antani.resentin.net.auth.TokenStore
import pm.antani.resentin.net.dto.AuthLoginRequestDto
import pm.antani.resentin.net.dto.ConfigDto
import pm.antani.resentin.net.rest.AuthApi
import pm.antani.resentin.net.rest.ConfigApi
import pm.antani.resentin.net.rest.MeApi

class AuthRepository(private val tokenStore: TokenStore) {

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
            200 -> checkNotNull(response.body()?.token) { "Risposta non valida" }
            202 -> error("Questo account richiede l'autenticazione a due fattori: usa un client token invece")
            401 -> error("Username o password non validi")
            429 -> error("Troppi tentativi falliti, riprova più tardi")
            else -> error("HTTP ${response.code()}")
        }
    }

    suspend fun verifyToken(host: String, token: String): Result<String> = runCatching {
        val retrofit = HttpClients.retrofit(host, HttpClients.okHttpClient { token })
        val response = retrofit.create(MeApi::class.java).getMe()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        checkNotNull(response.body()?.displayName) { "Risposta non valida" }
    }

    fun signIn(host: String, token: String, username: String) {
        tokenStore.saveSession(host, token, username)
    }

    fun signOut() {
        tokenStore.clear()
    }

    fun <T> api(serviceClass: Class<T>): T {
        val currentSession = checkNotNull(tokenStore.session.value) { "Non autenticato" }
        val retrofit = HttpClients.retrofit(currentSession.host, HttpClients.okHttpClient { currentSession.token })
        return retrofit.create(serviceClass)
    }
}
