package pm.antani.resentin.ui.channelsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.antani.resentin.data.prefs.AppPreferences
import pm.antani.resentin.data.prefs.channelKey
import pm.antani.resentin.domain.repository.MembersRepository
import pm.antani.resentin.domain.repository.NetworksRepository
import pm.antani.resentin.domain.repository.ServerMute
import pm.antani.resentin.domain.repository.UserSettingsRepository
import pm.antani.resentin.net.dto.BanlistEntryDto
import pm.antani.resentin.net.dto.ChannelModesEntryDto
import pm.antani.resentin.ui.common.isPrivileged
import pm.antani.resentin.ui.common.sigilsOf

/** Toggle-only simple channel modes offered as switches — the common cross-ircd
 * subset (n/t/m/i/s). Anything else (limit, key, ...) goes through [rawModeInput]. */
val SIMPLE_TOGGLE_MODES: List<Char> = listOf('n', 't', 'm', 'i', 's')

data class ChannelSettingsUiState(
    val topic: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val parted: Boolean = false,
    val isPrivileged: Boolean = false,
    val modes: ChannelModesEntryDto = ChannelModesEntryDto(),
    val rawModeInput: String = "",
    val listModeLetters: List<String> = emptyList(),
    val activeListMode: String = "b",
    val banlistEntries: List<BanlistEntryDto> = emptyList(),
    val banlistLoading: Boolean = false,
    val newMaskInput: String = "",
)

class ChannelSettingsViewModel(
    private val networksRepository: NetworksRepository,
    private val membersRepository: MembersRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val appPreferences: AppPreferences,
    private val networkSlug: String,
    private val channelName: String,
    private val username: String,
    private val subject: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelSettingsUiState())
    val uiState: StateFlow<ChannelSettingsUiState> = _uiState.asStateFlow()

    /** Local-only pin flag (DataStore, never on the server). */
    val isPinned: StateFlow<Boolean> = appPreferences.pinnedChannels
        .map { channelKey(networkSlug, channelName) in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun togglePinned() {
        viewModelScope.launch {
            appPreferences.setChannelPinned(networkSlug, channelName, !isPinned.value)
        }
    }

    /** Server mute (muted_targets) — same silence every device sees. Refreshed on
     * open so the switch never shows a stale cache. */
    val serverMute: StateFlow<ServerMute> = userSettingsRepository.muteFlowFor(networkSlug, channelName)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerMute(loaded = false))

    /** Server presence pin for this chat ("show"/"hide"/null = default). */
    val presencePin: StateFlow<String?> = userSettingsRepository.presencePinFlowFor(networkSlug, channelName)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPresencePin(pin: String?) {
        viewModelScope.launch {
            userSettingsRepository.setPresencePin(networkSlug, channelName, pin)
                .onFailure { _uiState.update { s -> s.copy(error = it.message) } }
        }
    }

    fun muteForever() = setMuteRemote(until = null)

    fun muteFor(durationSeconds: Long) =
        setMuteRemote(until = System.currentTimeMillis() / 1000 + durationSeconds)

    fun muteUntil(epochSeconds: Long) = setMuteRemote(until = epochSeconds)

    fun unmute() {
        viewModelScope.launch {
            userSettingsRepository.clearMute(networkSlug, channelName)
                .onFailure { _uiState.update { s -> s.copy(error = it.message) } }
        }
    }

    private fun setMuteRemote(until: Long?) {
        viewModelScope.launch {
            userSettingsRepository.setMute(networkSlug, channelName, until)
                .onFailure { _uiState.update { s -> s.copy(error = it.message) } }
        }
    }

    init {
        viewModelScope.launch {
            // One-shot seed of the current topic — the field must not be silently
            // overwritten by a later Room update while the user is mid-edit.
            val channel = networksRepository.observeChannel(networkSlug, channelName).filterNotNull().first()
            _uiState.update { it.copy(topic = channel.topic.orEmpty()) }
        }
        // Fresh server mute + presence state for the switches below (plus durations):
        // the app-start warm-up may not have run yet, or another device changed it.
        viewModelScope.launch {
            userSettingsRepository.refreshNotificationPrefs()
                .onFailure { _uiState.update { s -> s.copy(error = it.message) } }
            userSettingsRepository.refreshDisplayPrefs()
                .onFailure { _uiState.update { s -> s.copy(error = it.message) } }
        }
        networksRepository.observeChannelModes(networkSlug, channelName)
            .onEach { modes -> _uiState.update { it.copy(modes = modes) } }
            .launchIn(viewModelScope)
        membersRepository.observeListModesQueryable(networkSlug)
            .onEach { letters -> _uiState.update { it.copy(listModeLetters = letters) } }
            .launchIn(viewModelScope)
        membersRepository.observeMembers(networkSlug, channelName)
            .onEach { members ->
                val mine = members.find { it.nick.equals(username, ignoreCase = true) }
                _uiState.update { it.copy(isPrivileged = mine?.let { m -> isPrivileged(sigilsOf(m)) } ?: false) }
            }
            .launchIn(viewModelScope)
        membersRepository.banlistEvents
            .filter { it.network == networkSlug && it.channel.equals(channelName, ignoreCase = true) }
            .filter { it.mode == _uiState.value.activeListMode }
            .onEach { bundle -> _uiState.update { it.copy(banlistEntries = bundle.entries, banlistLoading = false) } }
            .launchIn(viewModelScope)
        refreshBanlist()
    }

    fun onTopicChange(value: String) = _uiState.update { it.copy(topic = value, saved = false) }

    fun saveTopic() {
        val topic = _uiState.value.topic
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            networksRepository.updateTopic(networkSlug, channelName, topic)
                .onSuccess { _uiState.update { it.copy(isSaving = false, saved = true) } }
                .onFailure { _uiState.update { s -> s.copy(isSaving = false, error = it.message) } }
        }
    }

    fun part() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            // Dalle impostazioni del canale si invia sempre il PART configurato
            // (stessa disciplina della Home: predefinito versionato se mai
            // personalizzato, nessun motivo se il campo è vuoto).
            networksRepository.partChannel(networkSlug, channelName, userSettingsRepository.partMessageForSend())
                .onSuccess { _uiState.update { it.copy(isSaving = false, parted = true) } }
                .onFailure { _uiState.update { s -> s.copy(isSaving = false, error = it.message) } }
        }
    }

    fun toggleSimpleMode(letter: Char) {
        val grant = letter !in _uiState.value.modes.modes.map { it.firstOrNull() }
        runVerb { networkId -> membersRepository.setMode(subject, networkId, channelName, "${if (grant) "+" else "-"}$letter", emptyList()) }
    }

    fun onRawModeInputChange(value: String) = _uiState.update { it.copy(rawModeInput = value) }

    /** Free-form fallback for modes this screen has no dedicated control for (limit,
     * key, ...) — e.g. `+l 50` or `-k`. First token is the mode string, the rest are
     * positional params. */
    fun applyRawMode() {
        val raw = _uiState.value.rawModeInput.trim()
        if (raw.isBlank()) return
        val parts = raw.split(" ").filter { it.isNotBlank() }
        val modeString = parts.first()
        val params = parts.drop(1)
        runVerb { networkId -> membersRepository.setMode(subject, networkId, channelName, modeString, params) }
        _uiState.update { it.copy(rawModeInput = "") }
    }

    fun selectListMode(mode: String) {
        _uiState.update { it.copy(activeListMode = mode, banlistEntries = emptyList()) }
        refreshBanlist()
    }

    fun refreshBanlist() {
        _uiState.update { it.copy(banlistLoading = true) }
        runVerb { networkId -> membersRepository.requestBanlist(subject, networkId, channelName, _uiState.value.activeListMode) }
    }

    fun onNewMaskChange(value: String) = _uiState.update { it.copy(newMaskInput = value) }

    fun addListModeEntry() {
        val mask = _uiState.value.newMaskInput.trim()
        if (mask.isBlank()) return
        _uiState.update { it.copy(newMaskInput = "") }
        setListModeEntry(grant = true, mask = mask)
    }

    fun removeListModeEntry(mask: String) = setListModeEntry(grant = false, mask = mask)

    private fun setListModeEntry(grant: Boolean, mask: String) {
        viewModelScope.launch {
            runCatching {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                val letter = _uiState.value.activeListMode
                membersRepository.setMode(subject, networkId, channelName, "${if (grant) "+" else "-"}$letter", listOf(mask))
            }.onFailure { _uiState.update { s -> s.copy(error = it.message) } }
            // List-mode changes aren't echoed as a channel_modes_changed snapshot (that
            // carries only the current SIMPLE modes) — re-query the list to reflect it.
            delay(500)
            refreshBanlist()
        }
    }

    private fun runVerb(action: suspend (networkId: Int) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                action(networkId)
            }.onFailure { _uiState.update { s -> s.copy(error = it.message) } }
        }
    }

    companion object {
        fun factory(
            networksRepository: NetworksRepository,
            membersRepository: MembersRepository,
            userSettingsRepository: UserSettingsRepository,
            appPreferences: AppPreferences,
            networkSlug: String,
            channelName: String,
            username: String,
            subject: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return ChannelSettingsViewModel(networksRepository, membersRepository, userSettingsRepository, appPreferences, networkSlug, channelName, username, subject) as T
            }
        }
    }
}
