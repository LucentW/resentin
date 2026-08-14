package pm.antani.resentin.domain.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import pm.antani.resentin.R
import pm.antani.resentin.data.db.AppDatabase
import pm.antani.resentin.data.prefs.AppPreferences
import pm.antani.resentin.domain.session.ConnectionManager
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
    private val connectionManager: ConnectionManager,
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

    suspend fun verifyToken(host: String, token: String): Result<MeDto> = runCatching {
        val retrofit = HttpClients.retrofit(host, HttpClients.okHttpClient { token })
        val response = retrofit.create(MeApi::class.java).getMe()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        checkNotNull(response.body()) { context.getString(R.string.auth_error_invalid_response) }
    }

    /** Visitor login — `POST /auth/login` with only `identifier` (no password) mints
     * an anonymous bearer for [nick] (`Grappa.Visitors.Login.login/2`), exactly like
     * the "continue as visitor" mode some grappa-irc instances advertise (e.g.
     * irc.sindro.me). A nick already held by another live anonymous session answers
     * 409 `anon_collision` — surfaced with its own message since a retry under a
     * different nick fixes it immediately, unlike a generic failure. 503 covers the
     * server's own admission caps (`ip_cap_exceeded`/`visitor_cap_exceeded`/
     * `too_many_sessions`) — a busy public bouncer genuinely runs out of anonymous
     * slots sometimes, so this is worth its own message rather than "HTTP 503". */
    suspend fun loginAsVisitor(host: String, nick: String): Result<String> = runCatching {
        val retrofit = HttpClients.retrofit(host, HttpClients.okHttpClient())
        val response = retrofit.create(AuthApi::class.java).login(AuthLoginRequestDto(nick))
        when (response.code()) {
            200 -> checkNotNull(response.body()?.token) { context.getString(R.string.auth_error_invalid_response) }
            409 -> error(context.getString(R.string.auth_error_visitor_nick_taken))
            429 -> error(context.getString(R.string.auth_error_too_many_attempts))
            503 -> error(context.getString(R.string.auth_error_visitor_capacity))
            else -> error("HTTP ${response.code()}")
        }
    }

    /** Every cached table (networks/channels/messages/members/...) is keyed by network
     * *slug* alone with no host column, so switching to a different grappa server whose
     * network happens to share a slug (e.g. two servers both naming a network "azzurra")
     * would otherwise silently merge their data — wrong read-cursor state, cross-server
     * message bleed, the works. Wiping the cache on a detected host change keeps the
     * single-server-at-a-time schema assumption actually true. */
    suspend fun signIn(host: String, token: String, username: String, wsSubject: String, kind: String) {
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
        tokenStore.saveSession(host, token, username, wsSubject, kind)
    }

    suspend fun getMe(): Result<MeDto> = runCatching {
        val response = api(MeApi::class.java).getMe()
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        checkNotNull(response.body())
    }

    /** Local-only logout: drops the WS connection and clears the stored session,
     * with no server round-trip. Used by the 401 force-logout path (the bearer is
     * already known-dead server-side, so a DELETE would just answer 401 again) —
     * everywhere else prefer [detach], which also revokes the session server-side. */
    fun signOut() {
        connectionManager.disconnect()
        tokenStore.clear()
    }

    /** "Detach" (cicchetto parity) — best-effort `DELETE /auth/logout`, then always
     * clears the local session regardless of the network outcome (a flaky connection
     * must never leave the user stuck "logged in" locally). For a registered user or
     * a NickServ-identified visitor this revokes only THIS session — the server-side
     * IRC connection stays up (bouncer-style, `AuthController.logout/2`'s moduledoc);
     * an ephemeral/anon visitor has no persistent identity to keep, so the server
     * tears its whole session down too — see [loginAsVisitor] and grappa-irc's own
     * `DELETE /auth/logout` contract. Tearing an identity's IRC connection down first
     * ("quit") is composed by the caller (see HomeViewModel.quit) before calling this. */
    suspend fun detach() {
        runCatching { api(AuthApi::class.java).logout() }
        signOut()
    }

    fun <T> api(serviceClass: Class<T>): T {
        val currentSession = checkNotNull(tokenStore.session.value) { context.getString(R.string.auth_error_not_authenticated) }
        val okHttpClient = HttpClients.okHttpClient(
            tokenProvider = { currentSession.token },
            // A 401 here means the bearer is already dead server-side (expired,
            // revoked, or the account/session was deleted) — nothing to retry.
            // Clearing the session lets AppRoot's reactive collector fall back to
            // the login screen on its own.
            onUnauthorized = ::signOut,
        )
        val retrofit = HttpClients.retrofit(currentSession.host, okHttpClient)
        return retrofit.create(serviceClass)
    }
}
