package pm.antani.resentin.ui.directory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.antani.resentin.R
import pm.antani.resentin.domain.repository.DirectorySyncEvent
import pm.antani.resentin.domain.repository.NetworksRepository
import pm.antani.resentin.irc.canonicalTarget
import pm.antani.resentin.net.dto.DirectoryEntryDto
import pm.antani.resentin.net.dto.DirectoryPageDto
import pm.antani.resentin.net.dto.FeaturedChannelDto

private const val DIRECTORY_EVENT_FALLBACK_DELAY_MS = 10_000L

data class DirectoryUiState(
    val entries: List<DirectoryEntryDto> = emptyList(),
    val featured: List<FeaturedChannelDto> = emptyList(),
    val joinedChannels: Set<String> = emptySet(),
    val status: String = "empty",
    val capturedAt: String? = null,
    val sort: String = "users",
    val query: String = "",
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshProgress: Int? = null,
    val isFeaturedLoading: Boolean = false,
    val error: String? = null,
    val featuredError: String? = null,
    val joined: String? = null,
)

class DirectoryViewModel(
    private val networksRepository: NetworksRepository,
    private val networkSlug: String,
    private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DirectoryUiState())
    val uiState: StateFlow<DirectoryUiState> = _uiState.asStateFlow()
    private var featuredRequestId = 0
    private var refreshFallbackJob: Job? = null

    init {
        viewModelScope.launch {
            networksRepository.networksWithChannels.collect { networks ->
                val joined = networks.firstOrNull { it.network.slug == networkSlug }
                    ?.channels
                    ?.filter { it.joined }
                    ?.map { canonicalTarget(it.name) }
                    ?.toSet()
                    ?: emptySet()
                _uiState.update { it.copy(joinedChannels = joined) }
            }
        }
        viewModelScope.launch {
            networksRepository.directoryEvents
                .filter { it.networkSlug == networkSlug }
                .collect { event -> handleDirectoryEvent(event) }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetchPage(cursor = null).onSuccess { page ->
                applyPage(page)
                _uiState.update { it.copy(isLoading = false) }
            }.onFailure {
                _uiState.update { state -> state.copy(isLoading = false, error = it.message ?: context.getString(R.string.home_unknown_error)) }
            }
        }
    }

    fun loadFeatured() {
        val requestId = ++featuredRequestId
        viewModelScope.launch {
            _uiState.update { it.copy(isFeaturedLoading = true, featuredError = null) }
            val result = networksRepository.getFeaturedChannels(networkSlug)
            if (requestId != featuredRequestId) return@launch
            _uiState.update { state ->
                reduceFeaturedResult(
                    state,
                    result,
                    context.getString(R.string.directory_featured_unavailable),
                )
            }
        }
    }

    fun setSort(sort: String) {
        if (sort == _uiState.value.sort) return
        _uiState.update { it.copy(sort = sort) }
        load()
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun search() = load()

    fun loadMore() {
        val cursor = _uiState.value.nextCursor ?: return
        if (_uiState.value.isLoadingMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            fetchPage(cursor).onSuccess { page ->
                _uiState.update { state ->
                    state.copy(
                        isLoadingMore = false,
                        entries = state.entries + page.entries,
                        nextCursor = page.nextCursor,
                        status = page.status,
                        capturedAt = page.capturedAt,
                    )
                }
            }.onFailure {
                _uiState.update { state -> state.copy(isLoadingMore = false, error = it.message ?: context.getString(R.string.home_unknown_error)) }
            }
        }
    }

    /** Starts a server-side LIST capture. Progress and completion arrive through the
     * WebSocket; a single delayed GET is kept as a bounded recovery path if an event is lost. */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        refreshFallbackJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = it.entries.isEmpty() && it.featured.isEmpty(),
                isRefreshing = true,
                refreshProgress = 0,
                error = null,
            )
        }
        loadFeatured()
        viewModelScope.launch {
            fetchPage(cursor = null)
                .onSuccess { page ->
                    applyPage(page)
                    _uiState.update { it.copy(isLoading = false) }
                }
                .onFailure { failure ->
                    _uiState.update {
                        it.copy(isLoading = false, error = failure.message ?: context.getString(R.string.home_unknown_error))
                    }
                }

            networksRepository.refreshDirectory(networkSlug).onFailure { failure ->
                refreshFallbackJob?.cancel()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        refreshProgress = null,
                        error = failure.message ?: context.getString(R.string.home_unknown_error),
                    )
                }
                return@launch
            }

            refreshFallbackJob = viewModelScope.launch {
                delay(DIRECTORY_EVENT_FALLBACK_DELAY_MS)
                if (!_uiState.value.isRefreshing) return@launch
                fetchPage(cursor = null)
                    .onSuccess { page ->
                        applyPage(page)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                refreshProgress = null,
                                error = if (page.status == "refreshing") {
                                    context.getString(R.string.directory_refresh_timeout)
                                } else null,
                            )
                        }
                    }
                    .onFailure { failure ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                refreshProgress = null,
                                error = failure.message ?: context.getString(R.string.home_unknown_error),
                            )
                        }
                    }
            }
        }
    }

    private suspend fun handleDirectoryEvent(event: DirectorySyncEvent) {
        if (!_uiState.value.isRefreshing) return
        when (event) {
            is DirectorySyncEvent.Progress -> {
                _uiState.update { it.copy(refreshProgress = event.count) }
            }
            is DirectorySyncEvent.Complete -> {
                refreshFallbackJob?.cancel()
                fetchPage(cursor = null)
                    .onSuccess { page ->
                        applyPage(page)
                        _uiState.update { it.copy(isLoading = false, isRefreshing = false, refreshProgress = null, error = null) }
                    }
                    .onFailure { failure ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                refreshProgress = null,
                                error = failure.message ?: context.getString(R.string.home_unknown_error),
                            )
                        }
                    }
            }
            is DirectorySyncEvent.Failed -> {
                refreshFallbackJob?.cancel()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        refreshProgress = null,
                        error = event.reason.ifBlank { context.getString(R.string.home_unknown_error) },
                    )
                }
            }
        }
    }

    fun joinChannel(name: String) {
        if (isJoinedChannel(name, _uiState.value.joinedChannels)) {
            _uiState.update { it.copy(joined = name) }
            return
        }
        viewModelScope.launch {
            networksRepository.joinChannel(networkSlug, name)
                .onSuccess { _uiState.update { it.copy(joined = name) } }
                .onFailure { _uiState.update { state -> state.copy(error = it.message ?: context.getString(R.string.home_unknown_error)) } }
        }
    }

    fun consumeJoined() {
        _uiState.update { it.copy(joined = null) }
    }

    private fun applyPage(page: DirectoryPageDto) {
        _uiState.update {
            it.copy(
                entries = page.entries,
                nextCursor = page.nextCursor,
                status = page.status,
                capturedAt = page.capturedAt,
            )
        }
    }

    private suspend fun fetchPage(cursor: String?): Result<DirectoryPageDto> {
        val state = _uiState.value
        return networksRepository.getDirectory(networkSlug, state.sort, state.query.ifBlank { null }, cursor)
    }

    override fun onCleared() {
        refreshFallbackJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(networksRepository: NetworksRepository, networkSlug: String, context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return DirectoryViewModel(networksRepository, networkSlug, context.applicationContext) as T
                }
            }
    }
}


internal fun reduceFeaturedResult(
    state: DirectoryUiState,
    result: Result<List<FeaturedChannelDto>>,
    errorMessage: String,
): DirectoryUiState = result.fold(
    onSuccess = { featured ->
        state.copy(featured = featured, isFeaturedLoading = false, featuredError = null)
    },
    onFailure = {
        state.copy(isFeaturedLoading = false, featuredError = errorMessage)
    },
)

internal fun isJoinedChannel(name: String, joinedChannels: Set<String>): Boolean =
    canonicalTarget(name) in joinedChannels
