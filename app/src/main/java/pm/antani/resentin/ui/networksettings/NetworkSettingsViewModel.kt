package pm.antani.resentin.ui.networksettings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pm.antani.resentin.domain.repository.IgnoresRepository
import pm.antani.resentin.domain.repository.NetworksRepository
import pm.antani.resentin.net.dto.unreadMentionsRollup
import pm.antani.resentin.net.dto.unreadMessagesRollup
import pm.antani.resentin.ui.chat.readUploadFile

data class NetworkSettingsUiState(
    val slug: String = "",
    val nick: String = "",
    val ident: String = "",
    val realname: String = "",
    // Both credential fields are write-only. Blank means "leave unchanged".
    val nickservPassword: String = "",
    val serverPass: String = "",
    val serverPassSet: Boolean = false,
    val serverPassClearRequested: Boolean = false,
    val connected: Boolean = true,
    val performList: String = "",
    // KVIrc-style CTCP USERINFO profile — `gender` is one of "", "male", "female",
    // "nonbinary" (empty = unset), same closed set as `Credential.genders/0`.
    val profileAge: String = "",
    val profileGender: String = "",
    val profileLocation: String = "",
    val profileLanguages: String = "",
    val profileCustom: String = "",
    // M3a — the own avatar. No text field for the value itself: seeded from
    // `NetworkEntity.avatarUrl` (server-authoritative), decoded to a bitmap for preview.
    val avatarUrl: String? = null,
    val avatarBitmap: Bitmap? = null,
    val avatarUploading: Boolean = false,
    val avatarError: String? = null,
    val isLoading: Boolean = true,
    val networkFound: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    // Ignore list (#162) — draft/error for the add-mask field. The masks themselves
    // live in [NetworkSettingsViewModel.ignoredMasks], not here: that's a passthrough
    // of the shared [IgnoresRepository] cache, not screen-local state.
    val newIgnoreMask: String = "",
    val ignoreError: String? = null,
    // Notify / presence watch list (GH #247) — fetched on open, no live broadcast to
    // mirror (see [NetworksRepository.getNotifyList]), so it's plain screen state.
    val notifyList: List<String> = emptyList(),
    val notifyLoading: Boolean = true,
    val newNotifyNick: String = "",
    val notifyError: String? = null,
    // #2099 rollup — total unread across this network's archived windows, shown as
    // a badge on the archive launcher. Best-effort: a failure just leaves no badge,
    // the archive itself still loads (and reports its own error) when opened.
    val archiveUnreadMessages: Int = 0,
    val archiveUnreadMentions: Int = 0,
)

class NetworkSettingsViewModel(
    private val networksRepository: NetworksRepository,
    private val ignoresRepository: IgnoresRepository,
    private val appContext: Context,
    private val networkSlug: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworkSettingsUiState(slug = networkSlug))
    val uiState: StateFlow<NetworkSettingsUiState> = _uiState.asStateFlow()

    /** Ignored masks for this network — passthrough of the shared [IgnoresRepository]
     * cache (same list `/ignore` and `/unignore` mutate), refreshed on open below. */
    val ignoredMasks: StateFlow<List<String>> = ignoresRepository.ignores
        .map { it[networkSlug].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            ignoresRepository.refresh(networkSlug)
                .onFailure { error -> _uiState.update { it.copy(ignoreError = error.message) } }
        }
        refreshNotifyList()
        refreshArchiveUnread()
        viewModelScope.launch {
            networksRepository.archiveChanges
                .filter { it.networkSlug == networkSlug }
                .collect { refreshArchiveUnread() }
        }
        viewModelScope.launch {
            networksRepository.observeNetwork(networkSlug).collect { network ->
                if (network != null) {
                    val avatarChanged = network.avatarUrl != _uiState.value.avatarUrl
                    _uiState.update {
                        it.copy(
                            nick = network.nick,
                            ident = network.ident.orEmpty(),
                            realname = network.realname.orEmpty(),
                            connected = network.connectionState == "connected",
                            profileAge = network.profileAge.orEmpty(),
                            profileGender = network.profileGender.orEmpty(),
                            profileLocation = network.profileLocation.orEmpty(),
                            profileLanguages = network.profileLanguages.orEmpty(),
                            profileCustom = network.profileCustom.orEmpty(),
                            avatarUrl = network.avatarUrl,
                            avatarBitmap = if (avatarChanged) null else it.avatarBitmap,
                            isLoading = false,
                            networkFound = true,
                        )
                    }
                    if (avatarChanged) loadAvatarBitmap(network.avatarUrl)
                } else {
                    _uiState.update { it.copy(isLoading = false, networkFound = false) }
                }
            }
        }
        viewModelScope.launch {
            networksRepository.getPerform(networkSlug)
                .onSuccess { perform -> _uiState.update { it.copy(performList = perform.performList.orEmpty()) } }
        }
        viewModelScope.launch {
            networksRepository.getServerPassSet(networkSlug)
                .onSuccess { configured -> _uiState.update { it.copy(serverPassSet = configured) } }
                .onFailure { failure -> _uiState.update { it.copy(error = failure.message) } }
        }
    }

    private fun loadAvatarBitmap(url: String?) {
        viewModelScope.launch {
            if (url == null) return@launch
            val bitmap = withContext(Dispatchers.IO) {
                networksRepository.fetchAvatarBytes(url)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
            // Drop a stale result if the avatar moved on again while this fetch was in flight.
            if (_uiState.value.avatarUrl == url && bitmap != null) {
                _uiState.update { it.copy(avatarBitmap = bitmap) }
            }
        }
    }

    fun onNickChange(value: String) = _uiState.update { it.copy(nick = value, saved = false) }
    fun onIdentChange(value: String) = _uiState.update { it.copy(ident = value, saved = false) }
    fun onNickservPasswordChange(value: String) = _uiState.update { it.copy(nickservPassword = value, saved = false) }
    fun onServerPassChange(value: String) = _uiState.update {
        it.copy(serverPass = value, serverPassClearRequested = false, saved = false)
    }
    fun clearServerPass() = _uiState.update {
        it.copy(serverPass = "", serverPassClearRequested = true, saved = false)
    }
    fun onRealnameChange(value: String) = _uiState.update { it.copy(realname = value, saved = false) }
    fun onPerformChange(value: String) = _uiState.update { it.copy(performList = value, saved = false) }
    fun onProfileAgeChange(value: String) = _uiState.update { it.copy(profileAge = value, saved = false) }
    fun onProfileGenderChange(value: String) = _uiState.update { it.copy(profileGender = value, saved = false) }
    fun onProfileLocationChange(value: String) = _uiState.update { it.copy(profileLocation = value, saved = false) }
    fun onProfileLanguagesChange(value: String) = _uiState.update { it.copy(profileLanguages = value, saved = false) }
    fun onProfileCustomChange(value: String) = _uiState.update { it.copy(profileCustom = value, saved = false) }

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
            val profileResult = networksRepository.updateProfile(
                networkSlug,
                age = state.profileAge.trim(),
                gender = state.profileGender,
                location = state.profileLocation.trim(),
                languages = state.profileLanguages.trim(),
                custom = state.profileCustom.trim(),
            )
            val passwordResult: Result<Unit> =
                if (state.nickservPassword.isBlank()) {
                    Result.success(Unit)
                } else {
                    networksRepository.updateNetworkPassword(networkSlug, state.nickservPassword)
                }
            val serverPassResult: Result<Boolean> = when {
                state.serverPassClearRequested -> networksRepository.updateServerPass(networkSlug, "")
                state.serverPass.isNotBlank() -> networksRepository.updateServerPass(networkSlug, state.serverPass)
                else -> Result.success(state.serverPassSet)
            }
            val failure = listOf(
                identityResult.exceptionOrNull(),
                performResult.exceptionOrNull(),
                profileResult.exceptionOrNull(),
                passwordResult.exceptionOrNull(),
                serverPassResult.exceptionOrNull(),
            ).firstOrNull { it != null }
            _uiState.update {
                it.copy(
                    isSaving = false,
                    error = failure?.message,
                    saved = failure == null,
                    nickservPassword = if (passwordResult.isSuccess) "" else it.nickservPassword,
                    serverPass = if (serverPassResult.isSuccess) "" else it.serverPass,
                    serverPassSet = serverPassResult.getOrNull() ?: it.serverPassSet,
                    serverPassClearRequested = if (serverPassResult.isSuccess) false else it.serverPassClearRequested,
                )
            }
        }
    }

    /** M3a — uploads (or replaces) the own avatar. Fires immediately on file pick, not
     * deferred to [save]: the server never bounces the connection for it, same posture
     * as cicchetto's own editor. */
    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(avatarUploading = true, avatarError = null) }
            runCatching {
                val pending = readUploadFile(appContext, uri) ?: error("upload failed")
                networksRepository.uploadAvatar(networkSlug, pending.bytes, pending.mimeType).getOrThrow()
            }.onFailure { failure -> _uiState.update { it.copy(avatarError = failure.message) } }
            _uiState.update { it.copy(avatarUploading = false) }
        }
    }

    fun deleteAvatar() {
        viewModelScope.launch {
            _uiState.update { it.copy(avatarUploading = true, avatarError = null) }
            networksRepository.deleteAvatar(networkSlug)
                .onFailure { failure -> _uiState.update { it.copy(avatarError = failure.message) } }
            _uiState.update { it.copy(avatarUploading = false) }
        }
    }

    // -- Ignore list (#162) --------------------------------------------------

    fun onNewIgnoreMaskChange(value: String) = _uiState.update { it.copy(newIgnoreMask = value, ignoreError = null) }

    fun addIgnoreMask() {
        val mask = _uiState.value.newIgnoreMask.trim()
        if (mask.isEmpty()) return
        viewModelScope.launch {
            ignoresRepository.addIgnore(networkSlug, mask)
                .onSuccess { _uiState.update { it.copy(newIgnoreMask = "", ignoreError = null) } }
                .onFailure { error -> _uiState.update { it.copy(ignoreError = error.message) } }
        }
    }

    fun removeIgnoreMask(mask: String) {
        viewModelScope.launch {
            ignoresRepository.removeIgnore(networkSlug, mask)
                .onFailure { error -> _uiState.update { it.copy(ignoreError = error.message) } }
        }
    }

    // -- Notify / presence watch list (GH #247) -------------------------------

    fun refreshNotifyList() {
        viewModelScope.launch {
            _uiState.update { it.copy(notifyLoading = true) }
            networksRepository.getNotifyList(networkSlug)
                .onSuccess { list -> _uiState.update { it.copy(notifyList = list, notifyLoading = false, notifyError = null) } }
                .onFailure { error -> _uiState.update { it.copy(notifyLoading = false, notifyError = error.message) } }
        }
    }

    fun onNewNotifyNickChange(value: String) = _uiState.update { it.copy(newNotifyNick = value, notifyError = null) }

    fun addNotifyNick() {
        val nick = _uiState.value.newNotifyNick.trim()
        if (nick.isEmpty()) return
        viewModelScope.launch {
            networksRepository.addNotify(networkSlug, listOf(nick))
                .onSuccess {
                    _uiState.update { it.copy(newNotifyNick = "") }
                    refreshNotifyList()
                }
                .onFailure { error -> _uiState.update { it.copy(notifyError = error.message) } }
        }
    }

    fun removeNotifyNick(nick: String) {
        viewModelScope.launch {
            networksRepository.removeNotify(networkSlug, nick)
                .onSuccess { refreshNotifyList() }
                .onFailure { error -> _uiState.update { it.copy(notifyError = error.message) } }
        }
    }

    /** #2099 — loads the archive launcher rollup (see [getArchiveWithUnread]).
     * Silent on failure: no badge beats a blocking error for a count pill. */
    fun refreshArchiveUnread() {
        viewModelScope.launch {
            networksRepository.getArchiveWithUnread(networkSlug)
                .onSuccess { entries ->
                    _uiState.update {
                        it.copy(
                            archiveUnreadMessages = entries.unreadMessagesRollup(),
                            archiveUnreadMentions = entries.unreadMentionsRollup(),
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(
            networksRepository: NetworksRepository,
            ignoresRepository: IgnoresRepository,
            appContext: Context,
            networkSlug: String,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return NetworkSettingsViewModel(networksRepository, ignoresRepository, appContext, networkSlug) as T
                }
            }
    }
}
