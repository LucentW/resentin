package pm.antani.resentin.ui.login

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
    private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onHostChange(host: String) {
        _uiState.update { it.copy(host = host, error = null) }
    }

    /** Fires when the host field loses focus — if what's actually in there is a
     * pasted `grappa://host/token` URL rather than a hostname, split it into the two
     * fields instead of trying to sign in against a URL-shaped "host". Does not
     * submit on its own; the user still reviews + taps sign-in (unlike
     * [signInFromLink], which is the OS deep-link handoff and submits immediately). */
    fun onHostFieldBlur() {
        val (host, token) = parseGrappaLoginLink(_uiState.value.host) ?: return
        _uiState.update { it.copy(host = host, mode = LoginMode.TOKEN, token = token, error = null) }
    }

    /** The OS intent-filter handoff for a `grappa://host/token` magic link — unlike
     * [onHostFieldBlur], this is the user having tapped an actual link (e.g. a QR-code
     * login), so it submits right away instead of just prefilling the fields. */
    fun signInFromLink(host: String, token: String) {
        _uiState.update { it.copy(host = host, mode = LoginMode.TOKEN, token = token) }
        signIn()
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
            _uiState.update { it.copy(error = context.getString(R.string.login_error_host_blank)) }
            return
        }
        if (state.mode == LoginMode.TOKEN && state.token.isBlank()) {
            _uiState.update { it.copy(error = context.getString(R.string.login_error_token_blank)) }
            return
        }
        if (state.mode == LoginMode.PASSWORD && (state.username.isBlank() || state.password.isBlank())) {
            _uiState.update { it.copy(error = context.getString(R.string.login_error_credentials_blank)) }
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
                        error = context.getString(
                            R.string.login_error_server_unreachable,
                            configResult.exceptionOrNull()?.message,
                        ),
                    )
                }
                return@launch
            }
            if (config.minProtocolVersion > SUPPORTED_PROTOCOL_VERSION) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = context.getString(R.string.login_error_protocol_too_old, config.minProtocolVersion),
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
                _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.login_error_invalid_token)) }
                return@launch
            }

            authRepository.signIn(host, token, username)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    companion object {
        fun factory(authRepository: AuthRepository, context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return LoginViewModel(authRepository, context.applicationContext) as T
                }
            }
    }
}
