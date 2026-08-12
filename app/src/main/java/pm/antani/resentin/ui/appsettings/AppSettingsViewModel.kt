package pm.antani.resentin.ui.appsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.antani.resentin.data.prefs.AppPreferences
import pm.antani.resentin.data.prefs.ChatDisplayMode
import pm.antani.resentin.domain.repository.UserSettingsRepository
import pm.antani.resentin.net.dto.DisplayPrefsDto

data class AppSettingsUiState(
    val displayPrefs: DisplayPrefsDto = DisplayPrefsDto(),
    val aliases: Map<String, String> = emptyMap(),
    val newAliasName: String = "",
    val newAliasExpansion: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
)

class AppSettingsViewModel(
    private val userSettingsRepository: UserSettingsRepository,
    private val appPreferences: AppPreferences,
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
    }

    fun setStayConnected(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setStayConnected(enabled) }
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
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return AppSettingsViewModel(userSettingsRepository, appPreferences) as T
            }
        }
    }
}
