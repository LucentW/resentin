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
import pm.antani.resentin.net.dto.AddressingSettingsAdminDto
import pm.antani.resentin.net.dto.CredentialAdminDto
import pm.antani.resentin.net.dto.NetworkAdminDto
import pm.antani.resentin.net.dto.ServerAdminDto
import pm.antani.resentin.net.dto.SessionAdminDto
import pm.antani.resentin.net.dto.SessionLogEntryDto
import pm.antani.resentin.net.dto.SettingsAdminDto
import pm.antani.resentin.net.dto.UploadAdminDto
import pm.antani.resentin.net.dto.UploadSettingsAdminDto
import pm.antani.resentin.net.dto.UserAdminDto
import pm.antani.resentin.net.dto.VhostAdminDto
import pm.antani.resentin.net.dto.VisitorAdminDto

enum class AdminTab { NETWORKS, VHOSTS, USERS, SESSIONS, VISITORS, SETTINGS, SESSION_LOG }

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
    // Per-network server list: no GET /admin/networks/servers surface — the
    // list is per-network (GET /admin/networks/{id}/servers), so it's fetched
    // lazily on expand rather than N+1'd upfront for every network.
    val expandedNetworkIds: Set<Int> = emptySet(),
    val serversByNetworkId: Map<Int, List<ServerAdminDto>> = emptyMap(),
    val vhostHostCandidates: List<String> = emptyList(),
    // Per-user network access (issue: "can't manage a user's networks"). Flat,
    // server-wide list (GET /admin/credentials has no per-user filter) — the
    // "manage networks" dialog filters client-side by user id.
    val credentials: List<CredentialAdminDto> = emptyList(),
    val managingNetworksForUser: UserAdminDto? = null,
    // Settings + uploads registry — lazy-loaded on first visit to the tab
    // rather than upfront, like the per-network server lists.
    val settings: SettingsAdminDto? = null,
    val uploads: List<UploadAdminDto> = emptyList(),
    val settingsLoaded: Boolean = false,
    // Session log — same lazy-on-first-visit loading.
    val sessionLog: List<SessionLogEntryDto> = emptyList(),
    val sessionLogLoaded: Boolean = false,
)

class AdminViewModel(private val adminRepository: AdminRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        refreshAll()
    }

    fun selectTab(tab: AdminTab) {
        _uiState.update { it.copy(tab = tab) }
        if (tab == AdminTab.SETTINGS && !_uiState.value.settingsLoaded) refreshSettings()
        if (tab == AdminTab.SESSION_LOG && !_uiState.value.sessionLogLoaded) refreshSessionLog()
    }

    fun consumeError() = _uiState.update { it.copy(error = null) }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val networks = adminRepository.getNetworks()
            val vhosts = adminRepository.getVhosts()
            val users = adminRepository.getUsers()
            val sessions = adminRepository.getSessions()
            val visitors = adminRepository.getVisitors()
            val credentials = adminRepository.getCredentials()
            _uiState.update {
                it.copy(
                    networks = networks.getOrDefault(it.networks),
                    vhosts = vhosts.getOrNull()?.vhosts ?: it.vhosts,
                    vhostHostCandidates = vhosts.getOrNull()?.hostCandidates ?: it.vhostHostCandidates,
                    users = users.getOrDefault(it.users),
                    sessions = sessions.getOrDefault(it.sessions),
                    visitors = visitors.getOrDefault(it.visitors),
                    credentials = credentials.getOrDefault(it.credentials),
                    isLoading = false,
                    error = listOf(networks, vhosts, users, sessions, visitors, credentials)
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
        updateNetwork(
            network.slug,
            !network.visitorEnabled,
            network.visitorAutoconnect,
            network.maxConcurrentVisitorSessions,
            network.maxConcurrentUserSessions,
            network.maxPerIp,
        )
    }

    /** Full edit — everything the "edit network" dialog exposes, sent together
     * (the server keeps unsupplied caps as-is only when the KEY is absent; this
     * client always sends every field, so `null` here is a deliberate "clear to
     * unlimited", never "leave unchanged" — see AdminRepository.updateNetwork). */
    fun updateNetwork(
        slug: String,
        visitorEnabled: Boolean,
        visitorAutoconnect: Boolean,
        maxConcurrentVisitorSessions: Int?,
        maxConcurrentUserSessions: Int?,
        maxPerIp: Int?,
    ) {
        viewModelScope.launch {
            adminRepository.updateNetwork(
                slug,
                visitorEnabled,
                visitorAutoconnect,
                maxConcurrentVisitorSessions,
                maxConcurrentUserSessions,
                maxPerIp,
            )
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
                .onSuccess { server ->
                    _uiState.update { state ->
                        state.copy(
                            expandedNetworkIds = state.expandedNetworkIds + networkId,
                            serversByNetworkId = state.serversByNetworkId +
                                (networkId to (state.serversByNetworkId[networkId].orEmpty() + server)),
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    /** Toggles the per-network server list open/closed, fetching it on first
     * expand — there's no bulk GET, only GET /admin/networks/{id}/servers. */
    fun toggleNetworkServers(network: NetworkAdminDto) {
        val expanded = _uiState.value.expandedNetworkIds
        if (network.id in expanded) {
            _uiState.update { it.copy(expandedNetworkIds = expanded - network.id) }
            return
        }
        _uiState.update { it.copy(expandedNetworkIds = expanded + network.id) }
        if (network.id in _uiState.value.serversByNetworkId) return
        viewModelScope.launch {
            adminRepository.getServers(network.id)
                .onSuccess { servers -> _uiState.update { it.copy(serversByNetworkId = it.serversByNetworkId + (network.id to servers)) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    fun removeServer(networkId: Int, server: ServerAdminDto) {
        viewModelScope.launch {
            adminRepository.deleteServer(networkId, server.id)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            serversByNetworkId = state.serversByNetworkId +
                                (networkId to state.serversByNetworkId[networkId].orEmpty().filter { it.id != server.id }),
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    private fun replaceNetwork(updated: NetworkAdminDto) {
        _uiState.update { state -> state.copy(networks = state.networks.map { if (it.id == updated.id) updated else it }) }
    }

    // --- Vhosts -----------------------------------------------------------

    fun createVhost(address: String, inPool: Boolean, generallyAvailable: Boolean) {
        if (address.isBlank()) return
        viewModelScope.launch {
            // #228 rule mirrored client-side: in_pool implies generally_available
            // (a source drawn into the outbound rotation is, by construction, one
            // any auto-allocated session may pick).
            adminRepository.createVhost(address, inPool, generallyAvailable || inPool)
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

    fun rotateUserPassword(user: UserAdminDto, newPassword: String) {
        if (newPassword.isBlank()) return
        viewModelScope.launch {
            adminRepository.setUserPassword(user.id, newPassword)
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    // --- Credentials (per-user network access) -------------------------------

    fun openManageNetworks(user: UserAdminDto) = _uiState.update { it.copy(managingNetworksForUser = user) }

    fun closeManageNetworks() = _uiState.update { it.copy(managingNetworksForUser = null) }

    fun bindNetwork(user: UserAdminDto, network: NetworkAdminDto, nick: String, authMethod: String, password: String?) {
        if (nick.isBlank()) return
        viewModelScope.launch {
            adminRepository.createCredential(user.id, network.id, nick, authMethod, password?.ifBlank { null })
                .onSuccess { credential -> _uiState.update { it.copy(credentials = it.credentials + credential) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    fun unbindNetwork(user: UserAdminDto, network: NetworkAdminDto) {
        viewModelScope.launch {
            adminRepository.deleteCredential(user.id, network.id)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(credentials = state.credentials.filterNot { it.userId == user.id && it.networkId == network.id })
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    // --- Settings + uploads ------------------------------------------------------

    fun refreshSettings() {
        viewModelScope.launch {
            val settings = adminRepository.getSettings()
            val uploads = adminRepository.getUploads()
            _uiState.update {
                it.copy(
                    settings = settings.getOrNull() ?: it.settings,
                    uploads = uploads.getOrDefault(it.uploads),
                    settingsLoaded = true,
                    error = listOf(settings, uploads).firstNotNullOfOrNull { r -> r.exceptionOrNull()?.message },
                )
            }
        }
    }

    fun updateSettings(upload: UploadSettingsAdminDto, addressing: AddressingSettingsAdminDto) {
        viewModelScope.launch {
            adminRepository.updateSettings(upload, addressing)
                .onSuccess { updated -> _uiState.update { it.copy(settings = updated) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    fun deleteUpload(upload: UploadAdminDto) {
        viewModelScope.launch {
            adminRepository.deleteUpload(upload.id)
                .onSuccess { _uiState.update { it.copy(uploads = it.uploads.filter { u -> u.id != upload.id }) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    // --- Session log -------------------------------------------------------------

    fun refreshSessionLog() {
        viewModelScope.launch {
            adminRepository.getSessionLog()
                .onSuccess { entries -> _uiState.update { it.copy(sessionLog = entries, sessionLogLoaded = true) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message, sessionLogLoaded = true) } }
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
