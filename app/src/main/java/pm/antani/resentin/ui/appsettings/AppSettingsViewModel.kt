package pm.antani.resentin.ui.appsettings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.UnifiedPush
import pm.antani.resentin.data.prefs.AppPreferences
import pm.antani.resentin.data.prefs.ChatDisplayMode
import pm.antani.resentin.domain.repository.PushRepository
import pm.antani.resentin.domain.repository.UserSettingsRepository
import pm.antani.resentin.net.dto.DisplayPrefsDto
import pm.antani.resentin.net.dto.PushSubscriptionSummaryDto
import pm.antani.resentin.net.dto.VhostOptionDto

data class AppSettingsUiState(
    val displayPrefs: DisplayPrefsDto = DisplayPrefsDto(),
    val aliases: Map<String, String> = emptyMap(),
    val newAliasName: String = "",
    val newAliasExpansion: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val pushSubscriptions: List<PushSubscriptionSummaryDto> = emptyList(),
    val pushSubscriptionsLoading: Boolean = false,
    val ownPushSubscriptionId: String? = null,
    val pushError: String? = null,
    val vhostOptions: List<VhostOptionDto> = emptyList(),
    val vhostSelection: List<String> = emptyList(),
    val vhostError: String? = null,
)

class AppSettingsViewModel(
    private val userSettingsRepository: UserSettingsRepository,
    private val appPreferences: AppPreferences,
    private val pushRepository: PushRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppSettingsUiState())
    val uiState: StateFlow<AppSettingsUiState> = _uiState.asStateFlow()

    val stayConnected: StateFlow<Boolean> = appPreferences.stayConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val chatDisplayMode: StateFlow<ChatDisplayMode> = appPreferences.chatDisplayMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatDisplayMode.BUBBLES)

    fun setChatDisplayMode(mode: ChatDisplayMode) {
        viewModelScope.launch { appPreferences.setChatDisplayMode(mode) }
    }

    val showSeconds: StateFlow<Boolean> = appPreferences.showSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setShowSeconds(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setShowSeconds(enabled) }
    }

    init {
        viewModelScope.launch {
            val prefsResult = userSettingsRepository.getDisplayPrefs()
            val aliasesResult = userSettingsRepository.getAliases()
            _uiState.update {
                it.copy(
                    displayPrefs = prefsResult.getOrDefault(DisplayPrefsDto()),
                    aliases = aliasesResult.getOrNull().orEmpty(),
                    isLoading = false,
                    error = prefsResult.exceptionOrNull()?.message ?: aliasesResult.exceptionOrNull()?.message,
                )
            }
        }
        refreshVhostSettings()
    }

    fun refreshVhostSettings() {
        viewModelScope.launch {
            userSettingsRepository.getVhostSettings()
                .onSuccess { settings -> _uiState.update { it.copy(vhostOptions = settings.available, vhostSelection = settings.selection) } }
                .onFailure { error -> _uiState.update { it.copy(vhostError = error.message) } }
        }
    }

    /** Toggles [address] in the selection set. The server allows more than one
     * active selection (random pick per connection), so this is a multi-select,
     * not a radio choice. */
    fun toggleVhostSelection(address: String) {
        val current = _uiState.value.vhostSelection
        val updated = if (address in current) current - address else current + address
        viewModelScope.launch {
            userSettingsRepository.updateVhostSelection(updated)
                .onSuccess { settings -> _uiState.update { it.copy(vhostOptions = settings.available, vhostSelection = settings.selection, vhostError = null) } }
                .onFailure { error -> _uiState.update { it.copy(vhostError = error.message) } }
        }
    }

    fun setStayConnected(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setStayConnected(enabled) }
    }

    val pushEnabled: StateFlow<Boolean> = appPreferences.unifiedPushEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val pushDecryptionFailureAt: StateFlow<Long?> = appPreferences.pushDecryptionFailureAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Registration completes asynchronously, in a different component
        // (UnifiedPushService.onNewEndpoint, triggered by a distributor callback
        // that can land seconds after the toggle) — this ViewModel has no other
        // signal for "the POST to /push/subscriptions just succeeded" than watching
        // for this id to appear. Without it, the device list only ever reflects
        // whatever existed when the screen happened to load.
        viewModelScope.launch {
            appPreferences.unifiedPushSubscriptionId.distinctUntilChanged().collect { id ->
                _uiState.update { it.copy(ownPushSubscriptionId = id) }
                if (id != null) refreshPushSubscriptions()
            }
        }
        refreshPushSubscriptions()
    }

    fun refreshPushSubscriptions() {
        viewModelScope.launch {
            _uiState.update { it.copy(pushSubscriptionsLoading = true) }
            pushRepository.listSubscriptions()
                .onSuccess { subs -> _uiState.update { it.copy(pushSubscriptions = subs, pushSubscriptionsLoading = false) } }
                .onFailure { error -> _uiState.update { it.copy(pushSubscriptionsLoading = false, pushError = error.message) } }
        }
    }

    fun revokePushSubscription(id: String) {
        viewModelScope.launch {
            pushRepository.deleteSubscription(id)
                .onSuccess { refreshPushSubscriptions() }
                .onFailure { error -> _uiState.update { it.copy(pushError = error.message) } }
        }
    }

    /** Called once a UnifiedPush distributor is linked (see AppSettingsScreen's
     * `tryUseCurrentOrDefaultDistributor` callback, which needs an Activity context this
     * ViewModel doesn't hold) — fetches the server's VAPID key and requests registration.
     * The actual server-side subscription is created asynchronously once the distributor
     * replies with an endpoint, in `UnifiedPushService.onNewEndpoint`. */
    fun enablePushAfterDistributorLinked() {
        viewModelScope.launch {
            val vapid = pushRepository.fetchVapidPublicKey().getOrElse {
                _uiState.update { s -> s.copy(pushError = it.message) }
                return@launch
            }
            runCatching { UnifiedPush.register(appContext, vapid = vapid) }
                .onSuccess { appPreferences.setUnifiedPushEnabled(true) }
                .onFailure { error -> _uiState.update { it.copy(pushError = error.message) } }
        }
    }

    fun reportPushLinkFailed(message: String) {
        _uiState.update { it.copy(pushError = message) }
    }

    fun disablePush() {
        viewModelScope.launch {
            runCatching { UnifiedPush.unregister(appContext) }
            pushRepository.deleteOwnSubscription()
            appPreferences.setUnifiedPushEnabled(false)
            refreshPushSubscriptions()
        }
    }

    fun toggleColoredNicklist() {
        // Round-trip the full object — the server rejects a PUT missing fields like
        // presence_filter even when they're unrelated to this toggle.
        val updated = _uiState.value.displayPrefs.copy(coloredNicklist = !_uiState.value.displayPrefs.coloredNicklist)
        _uiState.update { it.copy(displayPrefs = updated) }
        viewModelScope.launch {
            userSettingsRepository.updateDisplayPrefs(updated)
                .onFailure { _uiState.update { s -> s.copy(error = it.message) } }
        }
    }

    fun onNewAliasNameChange(value: String) = _uiState.update { it.copy(newAliasName = value) }
    fun onNewAliasExpansionChange(value: String) = _uiState.update { it.copy(newAliasExpansion = value) }

    fun addAlias() {
        val state = _uiState.value
        val name = state.newAliasName.trim()
        val expansion = state.newAliasExpansion.trim()
        if (name.isBlank() || expansion.isBlank()) return
        val updated = state.aliases + (name to expansion)
        saveAliases(updated, clearInputs = true)
    }

    fun removeAlias(name: String) {
        val updated = _uiState.value.aliases - name
        saveAliases(updated, clearInputs = false)
    }

    private fun saveAliases(updated: Map<String, String>, clearInputs: Boolean) {
        viewModelScope.launch {
            userSettingsRepository.updateAliases(updated)
                .onSuccess { aliases ->
                    _uiState.update {
                        it.copy(
                            aliases = aliases,
                            newAliasName = if (clearInputs) "" else it.newAliasName,
                            newAliasExpansion = if (clearInputs) "" else it.newAliasExpansion,
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    companion object {
        fun factory(
            userSettingsRepository: UserSettingsRepository,
            appPreferences: AppPreferences,
            pushRepository: PushRepository,
            appContext: Context,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return AppSettingsViewModel(userSettingsRepository, appPreferences, pushRepository, appContext) as T
            }
        }
    }
}
