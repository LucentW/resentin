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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.UnifiedPush
import pm.antani.resentin.data.prefs.AppPreferences
import pm.antani.resentin.data.prefs.AppFontFamily
import pm.antani.resentin.data.prefs.ChatDisplayMode
import pm.antani.resentin.data.prefs.MessageDensity
import pm.antani.resentin.data.prefs.ReplyStyle
import pm.antani.resentin.data.prefs.ThemeMode
import pm.antani.resentin.domain.repository.AuthRepository
import pm.antani.resentin.domain.repository.ChatRepository
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
    val isAdmin: Boolean = false,
    val replyCustomTemplate: String = "",
    val replyCustomTemplateSaved: Boolean = false,
    val autoAwayCustomMode: Boolean = false,
    val autoAwayCustomDraft: String = "",
    val autoAwaySavingError: String? = null,
    val newHighlight: String = "",
    val highlightError: String? = null,
    val showPeerProfiles: Boolean = false,
    val showPeerProfilesSaving: Boolean = false,
    val showPeerProfilesError: String? = null,
)

class AppSettingsViewModel(
    private val userSettingsRepository: UserSettingsRepository,
    private val appPreferences: AppPreferences,
    private val pushRepository: PushRepository,
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppSettingsUiState())
    private val coloredNicklistSaveMutex = Mutex()
    val uiState: StateFlow<AppSettingsUiState> = _uiState.asStateFlow()

    private val _messageDbSizeBytes = MutableStateFlow(0L)
    val messageDbSizeBytes: StateFlow<Long> = _messageDbSizeBytes.asStateFlow()

    fun refreshMessageDbSize() {
        _messageDbSizeBytes.value = chatRepository.messageDatabaseSizeBytes()
    }

    /** The manual "svuota database messaggi" settings action. */
    fun clearMessageDatabase() {
        viewModelScope.launch {
            chatRepository.clearAllMessages()
            refreshMessageDbSize()
        }
    }

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

    val showHostmaskInEvents: StateFlow<Boolean> = appPreferences.showHostmaskInEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setShowHostmaskInEvents(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setShowHostmaskInEvents(enabled) }
    }

    val smartPresenceFilter: StateFlow<Boolean> = appPreferences.smartPresenceFilter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setSmartPresenceFilter(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setSmartPresenceFilter(enabled) }
    }

    val unreadFirst: StateFlow<Boolean> = appPreferences.unreadFirst
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setUnreadFirst(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setUnreadFirst(enabled) }
    }

    val fontScale: StateFlow<Float> = appPreferences.fontScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)

    fun setFontScale(scale: Float) {
        viewModelScope.launch { appPreferences.setFontScale(scale) }
    }

    val themeMode: StateFlow<ThemeMode> = appPreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appPreferences.setThemeMode(mode) }
    }

    val fontFamily: StateFlow<AppFontFamily> = appPreferences.fontFamily
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppFontFamily.SYSTEM)

    fun setFontFamily(fontFamily: AppFontFamily) {
        viewModelScope.launch { appPreferences.setFontFamily(fontFamily) }
    }

    val chatFontFamily: StateFlow<AppFontFamily> = appPreferences.chatFontFamily
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppFontFamily.SYSTEM)

    fun setChatFontFamily(fontFamily: AppFontFamily) {
        viewModelScope.launch { appPreferences.setChatFontFamily(fontFamily) }
    }

    val messageDensity: StateFlow<MessageDensity> = appPreferences.messageDensity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MessageDensity.NORMAL)

    fun setMessageDensity(density: MessageDensity) {
        viewModelScope.launch { appPreferences.setMessageDensity(density) }
    }

    val lineHeightScale: StateFlow<Float> = appPreferences.lineHeightScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)

    fun setLineHeightScale(scale: Float) {
        viewModelScope.launch { appPreferences.setLineHeightScale(scale) }
    }

    val replyStyle: StateFlow<ReplyStyle> = appPreferences.replyStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReplyStyle.NICK)

    fun setReplyStyle(style: ReplyStyle) {
        viewModelScope.launch { appPreferences.setReplyStyle(style) }
    }

    fun onReplyCustomTemplateChange(value: String) =
        _uiState.update { it.copy(replyCustomTemplate = value, replyCustomTemplateSaved = false) }

    fun saveReplyCustomTemplate() {
        viewModelScope.launch {
            appPreferences.setReplyCustomTemplate(_uiState.value.replyCustomTemplate)
            _uiState.update { it.copy(replyCustomTemplateSaved = true) }
        }
    }

    init {
        refreshMessageDbSize()
        viewModelScope.launch {
            val prefsResult = userSettingsRepository.getDisplayPrefs()
            val aliasesResult = userSettingsRepository.getAliases()
            val prefs = prefsResult.getOrDefault(DisplayPrefsDto())
            _uiState.update {
                it.copy(
                    displayPrefs = prefs,
                    aliases = aliasesResult.getOrNull().orEmpty(),
                    isLoading = false,
                    error = prefsResult.exceptionOrNull()?.message
                        ?: aliasesResult.exceptionOrNull()?.message
                )
            }
            appPreferences.setColoredNicklist(prefs.coloredNicklist)
        }
        viewModelScope.launch {
            authRepository.getMe().onSuccess { me -> _uiState.update { it.copy(isAdmin = me.isAdmin) } }
        }
        viewModelScope.launch {
            val saved = appPreferences.replyCustomTemplate.first()
            _uiState.update { it.copy(replyCustomTemplate = saved) }
        }
        refreshVhostSettings()
        refreshAutoAwayDebounce()
        refreshWatchlist()
        refreshShowPeerProfiles()
    }

    private fun refreshShowPeerProfiles() {
        viewModelScope.launch {
            userSettingsRepository.getShowPeerProfiles()
                .onSuccess { enabled -> _uiState.update { it.copy(showPeerProfiles = enabled) } }
        }
    }

    fun toggleShowPeerProfiles() {
        val target = !_uiState.value.showPeerProfiles
        viewModelScope.launch {
            _uiState.update { it.copy(showPeerProfilesSaving = true, showPeerProfilesError = null) }
            userSettingsRepository.updateShowPeerProfiles(target)
                .onSuccess { enabled -> _uiState.update { it.copy(showPeerProfiles = enabled, showPeerProfilesSaving = false) } }
                .onFailure { error ->
                    _uiState.update { it.copy(showPeerProfilesSaving = false, showPeerProfilesError = error.message) }
                }
        }
    }

    // `/hilight` watchlist — server-side patterns, cached in the repository (same
    // rationale as the auto-away preference above); this is just a passthrough plus
    // the add/remove affordances, which need the WS subject for the user topic.
    val highlightPatterns: StateFlow<List<String>?> = userSettingsRepository.highlightPatterns

    private fun watchlistSubject(): String? = authRepository.session.value?.wsSubject

    fun refreshWatchlist() {
        viewModelScope.launch {
            val subject = watchlistSubject() ?: return@launch
            userSettingsRepository.refreshWatchlist(subject)
                .onFailure { error -> _uiState.update { it.copy(highlightError = error.message) } }
        }
    }

    fun onNewHighlightChange(value: String) =
        _uiState.update { it.copy(newHighlight = value, highlightError = null) }

    fun addHighlight() {
        val pattern = _uiState.value.newHighlight.trim()
        if (pattern.isEmpty()) return
        viewModelScope.launch {
            val subject = watchlistSubject() ?: return@launch
            userSettingsRepository.addHighlight(subject, pattern)
                .onSuccess { _uiState.update { it.copy(newHighlight = "", highlightError = null) } }
                .onFailure { error -> _uiState.update { it.copy(highlightError = error.message) } }
        }
    }

    fun removeHighlight(pattern: String) {
        viewModelScope.launch {
            val subject = watchlistSubject() ?: return@launch
            userSettingsRepository.removeHighlight(subject, pattern)
                .onFailure { error -> _uiState.update { it.copy(highlightError = error.message) } }
        }
    }

    // #348 on grappa-irc — cached in the repository (not this ViewModel) so the live
    // `auto_away_debounce_changed` push keeps it current even while this screen isn't
    // composed; this StateFlow is just a passthrough.
    val autoAwayDebounceSeconds: StateFlow<Int?> = userSettingsRepository.autoAwayDebounceSeconds

    fun refreshAutoAwayDebounce() {
        viewModelScope.launch {
            userSettingsRepository.getAutoAwayDebounce()
                .onFailure { error -> _uiState.update { it.copy(autoAwaySavingError = error.message) } }
        }
    }

    /** A preset pick is an immediate save — [seconds] is `null` for "use site default"
     * or `0` for "off", matching the wire's own sentinels. */
    fun onAutoAwayPresetSelected(seconds: Int?) {
        _uiState.update { it.copy(autoAwayCustomMode = false) }
        saveAutoAwayDebounce(seconds)
    }

    /** Entering "custom" only opens the input, seeded from the current value — it is a
     * MODE, not a value; nothing is persisted until [saveAutoAwayCustomDraft]. */
    fun onAutoAwayCustomModeSelected() {
        val current = autoAwayDebounceSeconds.value
        _uiState.update {
            it.copy(
                autoAwayCustomMode = true,
                autoAwayCustomDraft = if (current == null || current == 0) "" else current.toString(),
            )
        }
    }

    fun onAutoAwayCustomDraftChange(value: String) =
        _uiState.update { it.copy(autoAwayCustomDraft = value, autoAwayCustomMode = true) }

    /** [invalidInputMessage] is pre-resolved by the caller (a `stringResource`, same
     * convention as [reportPushLinkFailed]'s caller) — this ViewModel has no Compose
     * context of its own to localize it from. */
    fun saveAutoAwayCustomDraft(invalidInputMessage: String) {
        val raw = _uiState.value.autoAwayCustomDraft.trim()
        val seconds = raw.toIntOrNull()
        if (seconds == null) {
            _uiState.update { it.copy(autoAwaySavingError = invalidInputMessage) }
            return
        }
        saveAutoAwayDebounce(seconds)
    }

    private fun saveAutoAwayDebounce(seconds: Int?) {
        viewModelScope.launch {
            userSettingsRepository.updateAutoAwayDebounce(seconds)
                .onSuccess { _uiState.update { it.copy(autoAwaySavingError = null) } }
                .onFailure { error -> _uiState.update { it.copy(autoAwaySavingError = error.message) } }
        }
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
        val previous = _uiState.value.displayPrefs
        val updated = previous.copy(coloredNicklist = !previous.coloredNicklist)
        _uiState.update { it.copy(displayPrefs = updated, error = null) }
        viewModelScope.launch {
            coloredNicklistSaveMutex.withLock {
                userSettingsRepository.updateDisplayPrefs(updated)
                    .onSuccess { serverPrefs ->
                        // Persist the local mirror only after the server accepted the
                        // change. The server response is authoritative if it normalizes
                        // any part of the full display-prefs object.
                        appPreferences.setColoredNicklist(serverPrefs.coloredNicklist)
                        _uiState.update { state ->
                            if (state.displayPrefs == updated) {
                                state.copy(displayPrefs = serverPrefs, error = null)
                            } else {
                                state.copy(error = null)
                            }
                        }
                    }
                    .onFailure { error ->
                        _uiState.update { state ->
                            if (state.displayPrefs == updated) {
                                state.copy(displayPrefs = previous, error = error.message)
                            } else {
                                state.copy(error = error.message)
                            }
                        }
                        // Do not overwrite a newer toggle, but roll back the local
                        // mirror when this request still represents the visible state.
                        if (_uiState.value.displayPrefs == previous) {
                            appPreferences.setColoredNicklist(previous.coloredNicklist)
                        }
                    }
            }
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
            authRepository: AuthRepository,
            chatRepository: ChatRepository,
            appContext: Context,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return AppSettingsViewModel(
                    userSettingsRepository,
                    appPreferences,
                    pushRepository,
                    authRepository,
                    chatRepository,
                    appContext,
                ) as T
            }
        }
    }
}
