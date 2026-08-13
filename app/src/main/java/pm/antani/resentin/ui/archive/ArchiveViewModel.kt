package pm.antani.resentin.ui.archive

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.antani.resentin.R
import pm.antani.resentin.domain.repository.MembersRepository
import pm.antani.resentin.domain.repository.NetworksRepository
import pm.antani.resentin.net.dto.ArchiveEntryDto

data class ArchiveUiState(
    val entries: List<ArchiveEntryDto> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val recovered: String? = null,
    val pendingDelete: ArchiveEntryDto? = null,
)

class ArchiveViewModel(
    private val networksRepository: NetworksRepository,
    private val membersRepository: MembersRepository,
    private val networkSlug: String,
    private val subject: String,
    private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArchiveUiState())
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            networksRepository.getArchive(networkSlug)
                .onSuccess { entries -> _uiState.update { it.copy(entries = entries, isLoading = false) } }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: context.getString(R.string.home_unknown_error)) }
                }
        }
    }

    /** Reopens an archived target — rejoins a channel, or re-opens a DM window for a
     * query — then signals [ArchiveUiState.recovered] so the screen can navigate to it.
     * The scrollback itself was never deleted (only [deleteEntry] does that), so it's
     * still there once the target is live again. */
    fun recover(entry: ArchiveEntryDto) {
        viewModelScope.launch {
            val result = if (entry.kind == "query") {
                runCatching {
                    val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                    membersRepository.openQueryWindow(subject, networkId, entry.target)
                }
            } else {
                networksRepository.joinChannel(networkSlug, entry.target)
            }
            result.onSuccess { _uiState.update { it.copy(recovered = entry.target) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message ?: context.getString(R.string.home_unknown_error)) } }
        }
    }

    fun consumeRecovered() = _uiState.update { it.copy(recovered = null) }

    fun confirmDelete(entry: ArchiveEntryDto) = _uiState.update { it.copy(pendingDelete = entry) }

    fun cancelDelete() = _uiState.update { it.copy(pendingDelete = null) }

    fun deleteConfirmed() {
        val entry = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            networksRepository.deleteArchiveEntry(networkSlug, entry.target)
                .onSuccess { _uiState.update { it.copy(entries = it.entries - entry, pendingDelete = null) } }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: context.getString(R.string.home_unknown_error), pendingDelete = null) }
                }
        }
    }

    companion object {
        fun factory(
            networksRepository: NetworksRepository,
            membersRepository: MembersRepository,
            networkSlug: String,
            subject: String,
            context: Context,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return ArchiveViewModel(networksRepository, membersRepository, networkSlug, subject, context.applicationContext) as T
            }
        }
    }
}
