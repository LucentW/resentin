package pm.antani.resentin.ui.networksettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.antani.resentin.domain.repository.NetworksRepository

data class NetworkSettingsUiState(
    val slug: String = "",
    val nick: String = "",
    val ident: String = "",
    val realname: String = "",
    val connected: Boolean = true,
    val performList: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class NetworkSettingsViewModel(
    private val networksRepository: NetworksRepository,
    private val networkSlug: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworkSettingsUiState(slug = networkSlug))
    val uiState: StateFlow<NetworkSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            networksRepository.observeNetwork(networkSlug).collect { network ->
                if (network != null) {
                    _uiState.update {
                        it.copy(
                            nick = network.nick,
                            ident = network.ident.orEmpty(),
                            realname = network.realname.orEmpty(),
                            connected = network.connectionState == "connected",
                            isLoading = false,
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            networksRepository.getPerform(networkSlug)
                .onSuccess { perform -> _uiState.update { it.copy(performList = perform.performList.orEmpty()) } }
        }
    }

    fun onNickChange(value: String) = _uiState.update { it.copy(nick = value, saved = false) }
    fun onIdentChange(value: String) = _uiState.update { it.copy(ident = value, saved = false) }
    fun onRealnameChange(value: String) = _uiState.update { it.copy(realname = value, saved = false) }
    fun onPerformChange(value: String) = _uiState.update { it.copy(performList = value, saved = false) }

    fun toggleConnection() {
        val target = !_uiState.value.connected
        viewModelScope.launch {
            networksRepository.updateConnectionState(networkSlug, target)
                .onFailure { _uiState.update { s -> s.copy(error = it.message) } }
        }
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val identityResult = networksRepository.updateIdentity(
                networkSlug,
                nick = state.nick.trim(),
                ident = state.ident.trim().ifBlank { null },
                realname = state.realname.trim().ifBlank { null },
            )
            val performResult = networksRepository.updatePerform(networkSlug, state.performList.ifBlank { null })
            val failure = identityResult.exceptionOrNull() ?: performResult.exceptionOrNull()
            _uiState.update { it.copy(isSaving = false, error = failure?.message, saved = failure == null) }
        }
    }

    companion object {
        fun factory(networksRepository: NetworksRepository, networkSlug: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return NetworkSettingsViewModel(networksRepository, networkSlug) as T
                }
            }
    }
}
