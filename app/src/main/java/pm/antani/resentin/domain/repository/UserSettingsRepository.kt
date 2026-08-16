package pm.antani.resentin.domain.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pm.antani.resentin.domain.events.WsEvent
import pm.antani.resentin.domain.session.ConnectionManager
import pm.antani.resentin.net.dto.AliasesEnvelopeDto
import pm.antani.resentin.net.dto.DisplayPrefsDto
import pm.antani.resentin.net.dto.DisplayPrefsEnvelopeDto
import pm.antani.resentin.net.dto.VhostSelectionUpdateDto
import pm.antani.resentin.net.dto.VhostSettingsDto
import pm.antani.resentin.net.rest.UserSettingsApi

class UserSettingsRepository(
    private val authRepository: AuthRepository,
    private val connectionManager: ConnectionManager,
) {

    // #348 on grappa-irc — cached auto-away preference. `null` covers both "not loaded
    // yet" and "no preference, server default applies": the two render identically
    // ("use site default"), same conflation cic's autoAway.ts signal makes. Lives here
    // (not per-ViewModel) because the live `auto_away_debounce_changed` push — fired for
    // every write on the subject's account, including from another device — needs
    // somewhere with app-wide lifetime to land, same as the WS-fed state other
    // repositories (e.g. MembersRepository) hold.
    private val _autoAwayDebounceSeconds = MutableStateFlow<Int?>(null)
    val autoAwayDebounceSeconds: StateFlow<Int?> = _autoAwayDebounceSeconds.asStateFlow()

    fun startListening(scope: CoroutineScope) {
        connectionManager.events
            .filterIsInstance<WsEvent.AutoAwayDebounceChanged>()
            .onEach { _autoAwayDebounceSeconds.value = it.debounce.autoAwayDebounceSeconds }
            .launchIn(scope)
    }

    suspend fun getDisplayPrefs(): Result<DisplayPrefsDto> = runCatching {
        authRepository.api(UserSettingsApi::class.java).getDisplayPrefs().displayPrefs
    }

    suspend fun updateDisplayPrefs(prefs: DisplayPrefsDto): Result<DisplayPrefsDto> = runCatching {
        authRepository.api(UserSettingsApi::class.java)
            .updateDisplayPrefs(DisplayPrefsEnvelopeDto(prefs)).displayPrefs
    }

    suspend fun getAliases(): Result<Map<String, String>> = runCatching {
        authRepository.api(UserSettingsApi::class.java).getAliases().aliases
    }

    suspend fun updateAliases(aliases: Map<String, String>): Result<Map<String, String>> = runCatching {
        authRepository.api(UserSettingsApi::class.java).updateAliases(AliasesEnvelopeDto(aliases)).aliases
    }

    suspend fun getVhostSettings(): Result<VhostSettingsDto> = runCatching {
        authRepository.api(UserSettingsApi::class.java).getVhostSettings()
    }

    suspend fun updateVhostSelection(selection: List<String>): Result<VhostSettingsDto> = runCatching {
        authRepository.api(UserSettingsApi::class.java).updateVhostSelection(VhostSelectionUpdateDto(selection))
    }

    suspend fun getAutoAwayDebounce(): Result<Int?> = runCatching {
        authRepository.api(UserSettingsApi::class.java).getAutoAwayDebounce().autoAwayDebounceSeconds
    }.onSuccess { _autoAwayDebounceSeconds.value = it }

    /** [seconds]: `null` clears the preference (site default applies), `0` switches
     * auto-away off, any other value is the grace period in seconds — the accepted
     * range is the server's to enforce, surfaced verbatim on rejection. */
    suspend fun updateAutoAwayDebounce(seconds: Int?): Result<Int?> = runCatching {
        val body = buildJsonObject {
            put("auto_away_debounce_seconds", seconds?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        authRepository.api(UserSettingsApi::class.java).updateAutoAwayDebounce(body).autoAwayDebounceSeconds
    }.onSuccess { _autoAwayDebounceSeconds.value = it }
}
