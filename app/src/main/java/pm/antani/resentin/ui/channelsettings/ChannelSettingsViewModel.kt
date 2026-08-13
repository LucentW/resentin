package pm.antani.resentin.ui.channelsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.antani.resentin.domain.repository.MembersRepository
import pm.antani.resentin.domain.repository.NetworksRepository
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
    private val networkSlug: String,
    private val channelName: String,
    private val username: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelSettingsUiState())
    val uiState: StateFlow<ChannelSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // One-shot seed of the current topic — the field must not be silently
            // overwritten by a later Room update while the user is mid-edit.
            val channel = networksRepository.observeChannel(networkSlug, channelName).filterNotNull().first()
            _uiState.update { it.copy(topic = channel.topic.orEmpty()) }
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
            networksRepository.partChannel(networkSlug, channelName)
                .onSuccess { _uiState.update { it.copy(isSaving = false, parted = true) } }
                .onFailure { _uiState.update { s -> s.copy(isSaving = false, error = it.message) } }
        }
    }

    fun toggleSimpleMode(letter: Char) {
        val grant = letter !in _uiState.value.modes.modes.map { it.firstOrNull() }
        runVerb { networkId -> membersRepository.setMode(username, networkId, channelName, "${if (grant) "+" else "-"}$letter", emptyList()) }
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
        runVerb { networkId -> membersRepository.setMode(username, networkId, channelName, modeString, params) }
        _uiState.update { it.copy(rawModeInput = "") }
    }

    fun selectListMode(mode: String) {
        _uiState.update { it.copy(activeListMode = mode, banlistEntries = emptyList()) }
        refreshBanlist()
    }

    fun refreshBanlist() {
        _uiState.update { it.copy(banlistLoading = true) }
        runVerb { networkId -> membersRepository.requestBanlist(username, networkId, channelName, _uiState.value.activeListMode) }
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
                membersRepository.setMode(username, networkId, channelName, "${if (grant) "+" else "-"}$letter", listOf(mask))
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
            networkSlug: String,
            channelName: String,
            username: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return ChannelSettingsViewModel(networksRepository, membersRepository, networkSlug, channelName, username) as T
            }
        }
    }
}
