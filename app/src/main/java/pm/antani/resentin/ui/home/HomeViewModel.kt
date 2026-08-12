package pm.antani.resentin.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pm.antani.resentin.data.db.NetworkWithChannels
import pm.antani.resentin.domain.repository.NetworksRepository

class HomeViewModel(private val networksRepository: NetworksRepository) : ViewModel() {

    val networks: StateFlow<List<NetworkWithChannels>> = networksRepository.networksWithChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            networksRepository.refresh()
                .onSuccess { _error.value = null }
                .onFailure { _error.value = it.message ?: "Errore sconosciuto" }
            _isRefreshing.value = false
        }
    }

    companion object {
        fun factory(networksRepository: NetworksRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return HomeViewModel(networksRepository) as T
                }
            }
    }
}
