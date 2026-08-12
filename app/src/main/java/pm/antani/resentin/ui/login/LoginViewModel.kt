package pm.antani.resentin.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.antani.resentin.domain.repository.AuthRepository

private const val SUPPORTED_PROTOCOL_VERSION = 1

enum class LoginMode { TOKEN, PASSWORD }

data class LoginUiState(
    val host: String = "",
    val mode: LoginMode = LoginMode.TOKEN,
    val token: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    defaultHost: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState(host = defaultHost))
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onHostChange(host: String) {
        _uiState.update { it.copy(host = host, error = null) }
    }

    fun onModeChange(mode: LoginMode) {
        _uiState.update { it.copy(mode = mode, error = null) }
    }

    fun onTokenChange(token: String) {
        _uiState.update { it.copy(token = token, error = null) }
    }

    fun onUsernameChange(username: String) {
        _uiState.update { it.copy(username = username, error = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun signIn() {
        val state = _uiState.value
        val host = state.host.trim().removePrefix("https://").removePrefix("http://").removeSuffix("/")

        if (host.isBlank()) {
            _uiState.update { it.copy(error = "Inserisci l'indirizzo del server") }
            return
        }
        if (state.mode == LoginMode.TOKEN && state.token.isBlank()) {
            _uiState.update { it.copy(error = "Inserisci il token") }
            return
        }
        if (state.mode == LoginMode.PASSWORD && (state.username.isBlank() || state.password.isBlank())) {
            _uiState.update { it.copy(error = "Inserisci username e password") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val configResult = authRepository.fetchServerConfig(host)
            val config = configResult.getOrNull()
            if (config == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Server non raggiungibile: ${configResult.exceptionOrNull()?.message}",
                    )
                }
                return@launch
            }
            if (config.minProtocolVersion > SUPPORTED_PROTOCOL_VERSION) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Il server richiede un protocollo più recente (min ${config.minProtocolVersion}): aggiorna l'app",
                    )
                }
                return@launch
            }

            val token = if (state.mode == LoginMode.TOKEN) {
                state.token.trim()
            } else {
                val loginResult = authRepository.loginWithPassword(host, state.username.trim(), state.password)
                val obtainedToken = loginResult.getOrNull()
                if (obtainedToken == null) {
                    _uiState.update { it.copy(isLoading = false, error = loginResult.exceptionOrNull()?.message) }
                    return@launch
                }
                obtainedToken
            }

            val verifyResult = authRepository.verifyToken(host, token)
            val username = verifyResult.getOrNull()
            if (username == null) {
                _uiState.update { it.copy(isLoading = false, error = "Token non valido o accesso negato") }
                return@launch
            }

            authRepository.signIn(host, token, username)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    companion object {
        fun factory(authRepository: AuthRepository, defaultHost: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return LoginViewModel(authRepository, defaultHost) as T
                }
            }
    }
}
