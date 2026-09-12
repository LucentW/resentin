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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pm.antani.resentin.domain.repository.NetworksRepository
import pm.antani.resentin.ui.chat.readUploadFile

data class NetworkSettingsUiState(
    val slug: String = "",
    val nick: String = "",
    val ident: String = "",
    val realname: String = "",
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
)

class NetworkSettingsViewModel(
    private val networksRepository: NetworksRepository,
    private val appContext: Context,
    private val networkSlug: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworkSettingsUiState(slug = networkSlug))
    val uiState: StateFlow<NetworkSettingsUiState> = _uiState.asStateFlow()

    init {
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
            val failure = identityResult.exceptionOrNull() ?: performResult.exceptionOrNull() ?: profileResult.exceptionOrNull()
            _uiState.update { it.copy(isSaving = false, error = failure?.message, saved = failure == null) }
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

    companion object {
        fun factory(
            networksRepository: NetworksRepository,
            appContext: Context,
            networkSlug: String,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return NetworkSettingsViewModel(networksRepository, appContext, networkSlug) as T
                }
            }
    }
}
