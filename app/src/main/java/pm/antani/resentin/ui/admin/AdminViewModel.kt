package pm.antani.resentin.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.antani.resentin.domain.repository.AdminRepository
import pm.antani.resentin.net.dto.NetworkAdminDto
import pm.antani.resentin.net.dto.SessionAdminDto
import pm.antani.resentin.net.dto.UserAdminDto
import pm.antani.resentin.net.dto.VhostAdminDto
import pm.antani.resentin.net.dto.VisitorAdminDto

enum class AdminTab { NETWORKS, VHOSTS, USERS, SESSIONS, VISITORS }

data class AdminUiState(
    val tab: AdminTab = AdminTab.NETWORKS,
    val networks: List<NetworkAdminDto> = emptyList(),
    val vhosts: List<VhostAdminDto> = emptyList(),
    val users: List<UserAdminDto> = emptyList(),
    val sessions: List<SessionAdminDto> = emptyList(),
    val visitors: List<VisitorAdminDto> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val lastSweepCount: Int? = null,
)

class AdminViewModel(private val adminRepository: AdminRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        refreshAll()
    }

    fun selectTab(tab: AdminTab) = _uiState.update { it.copy(tab = tab) }

    fun consumeError() = _uiState.update { it.copy(error = null) }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val networks = adminRepository.getNetworks()
            val vhosts = adminRepository.getVhosts()
            val users = adminRepository.getUsers()
            val sessions = adminRepository.getSessions()
            val visitors = adminRepository.getVisitors()
            _uiState.update {
                it.copy(
                    networks = networks.getOrDefault(it.networks),
                    vhosts = vhosts.getOrDefault(it.vhosts),
                    users = users.getOrDefault(it.users),
                    sessions = sessions.getOrDefault(it.sessions),
                    visitors = visitors.getOrDefault(it.visitors),
                    isLoading = false,
                    error = listOf(networks, vhosts, users, sessions, visitors)
                        .firstNotNullOfOrNull { r -> r.exceptionOrNull()?.message },
                )
            }
        }
    }

    // --- Networks -------------------------------------------------------

    fun createNetwork(slug: String) {
        if (slug.isBlank()) return
        viewModelScope.launch {
            adminRepository.createNetwork(slug)
                .onSuccess { net -> _uiState.update { it.copy(networks = it.networks + net) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    fun toggleVisitorEnabled(network: NetworkAdminDto) {
        viewModelScope.launch {
            adminRepository.updateNetwork(network.slug, !network.visitorEnabled, network.visitorAutoconnect)
                .onSuccess { updated -> replaceNetwork(updated) }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    fun deleteNetwork(network: NetworkAdminDto) {
        viewModelScope.launch {
            adminRepository.deleteNetwork(network.id)
                .onSuccess { _uiState.update { it.copy(networks = it.networks.filter { n -> n.id != network.id }) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    fun addServer(networkId: Int, host: String, port: Int, tls: Boolean) {
        if (host.isBlank()) return
        viewModelScope.launch {
            adminRepository.createServer(networkId, host, port, tls)
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    private fun replaceNetwork(updated: NetworkAdminDto) {
        _uiState.update { state -> state.copy(networks = state.networks.map { if (it.id == updated.id) updated else it }) }
    }

    // --- Vhosts -----------------------------------------------------------

    fun createVhost(address: String) {
        if (address.isBlank()) return
        viewModelScope.launch {
            adminRepository.createVhost(address)
                .onSuccess { vhost -> _uiState.update { it.copy(vhosts = it.vhosts + vhost) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    fun deleteVhost(vhost: VhostAdminDto) {
        viewModelScope.launch {
            adminRepository.deleteVhost(vhost.id)
                .onSuccess { _uiState.update { it.copy(vhosts = it.vhosts.filter { v -> v.id != vhost.id }) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    // --- Users --------------------------------------------------------------

    fun createUser(name: String, password: String) {
        if (name.isBlank() || password.isBlank()) return
        viewModelScope.launch {
            adminRepository.createUser(name, password, isAdmin = false)
                .onSuccess { user -> _uiState.update { it.copy(users = it.users + user) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    fun toggleUserAdmin(user: UserAdminDto) {
        viewModelScope.launch {
            adminRepository.setUserAdmin(user.id, !user.isAdmin)
                .onSuccess { updated -> _uiState.update { s -> s.copy(users = s.users.map { if (it.id == updated.id) updated else it }) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    fun deleteUser(user: UserAdminDto) {
        viewModelScope.launch {
            adminRepository.deleteUser(user.id)
                .onSuccess { _uiState.update { it.copy(users = it.users.filter { u -> u.id != user.id }) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    // --- Sessions -----------------------------------------------------------

    fun disconnectSession(session: SessionAdminDto) {
        viewModelScope.launch {
            adminRepository.disconnectSession(session.compositeId)
                .onSuccess { _uiState.update { it.copy(sessions = it.sessions.filter { s -> s.compositeId != session.compositeId }) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    fun killSession(session: SessionAdminDto) {
        viewModelScope.launch {
            adminRepository.killSession(session.compositeId)
                .onSuccess { _uiState.update { it.copy(sessions = it.sessions.filter { s -> s.compositeId != session.compositeId }) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    // --- Visitors + reaper ----------------------------------------------------

    fun deleteVisitor(visitor: VisitorAdminDto) {
        viewModelScope.launch {
            adminRepository.deleteVisitor(visitor.id)
                .onSuccess { _uiState.update { it.copy(visitors = it.visitors.filter { v -> v.id != visitor.id }) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    fun sweepVisitors() {
        viewModelScope.launch {
            adminRepository.runReaper()
                .onSuccess { result -> _uiState.update { it.copy(lastSweepCount = result.sweptCount) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
            adminRepository.getVisitors().onSuccess { visitors -> _uiState.update { it.copy(visitors = visitors) } }
        }
    }

    companion object {
        fun factory(adminRepository: AdminRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return AdminViewModel(adminRepository) as T
            }
        }
    }
}
