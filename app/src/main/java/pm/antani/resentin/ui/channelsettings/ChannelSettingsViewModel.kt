package pm.antani.resentin.ui.channelsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.antani.resentin.domain.repository.NetworksRepository

data class ChannelSettingsUiState(
    val topic: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val parted: Boolean = false,
)

class ChannelSettingsViewModel(
    private val networksRepository: NetworksRepository,
    private val networkSlug: String,
    private val channelName: String,
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

    companion object {
        fun factory(
            networksRepository: NetworksRepository,
            networkSlug: String,
            channelName: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return ChannelSettingsViewModel(networksRepository, networkSlug, channelName) as T
            }
        }
    }
}
