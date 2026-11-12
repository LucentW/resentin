package pm.antani.resentin.ui.e2e

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pm.antani.resentin.data.db.E2eMessageEntity
import pm.antani.resentin.domain.repository.E2eRepository
import pm.antani.resentin.e2e.IdentityState
import pm.antani.resentin.e2e.foldNick
import pm.antani.resentin.net.dto.E2eRequestDto

/** What the Home section shows: requests waiting on us or answered, conversations, and identities held elsewhere. */
data class E2eOverview(
    val incoming: List<Pair<String, E2eRequestDto>> = emptyList(),
    val answered: List<Pair<String, E2eRequestDto>> = emptyList(),
    val waiting: List<Pair<String, E2eRequestDto>> = emptyList(),
    val conversations: List<E2eMessageEntity> = emptyList(),
    val elsewhere: List<String> = emptyList(),
    /** Networks whose server speaks E2E at all (the sync answered): where a new chat can start. */
    val networks: List<String> = emptyList(),
) {
    val visible get() = networks.isNotEmpty()
}

class E2eOverviewViewModel(private val repo: E2eRepository) : ViewModel() {
    val overview: StateFlow<E2eOverview> = combine(repo.requests, repo.identityState, repo.allConversations()) { requests, identity, convs ->
        val all = requests.flatMap { (slug, list) -> list.map { slug to it } }
        E2eOverview(
            incoming = all.filter { it.second.state == "incoming" },
            answered = all.filter { it.second.state == "refused" || it.second.state == "expired" },
            waiting = all.filter { it.second.state == "outgoing" },
            conversations = convs,
            elsewhere = identity.filterValues { it is IdentityState.Elsewhere }.keys.toList(),
            networks = identity.keys.toList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), E2eOverview())

    fun accept(slug: String, r: E2eRequestDto, done: (Boolean) -> Unit = {}) = viewModelScope.launch { done(repo.accept(slug, r).isSuccess) }
    fun refuse(slug: String, r: E2eRequestDto) = viewModelScope.launch { repo.refuse(slug, r) }
    fun dismiss(slug: String, r: E2eRequestDto) = viewModelScope.launch { repo.dismissRequest(slug, r) }
    fun ask(slug: String, peer: String, done: (Boolean) -> Unit) = viewModelScope.launch { done(repo.ask(slug, peer).isSuccess) }
    fun replaceIdentity(slug: String) = viewModelScope.launch { repo.replaceIdentity(slug) }

    companion object {
        fun factory(repo: E2eRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = E2eOverviewViewModel(repo) as T
        }
    }
}

/** One encrypted conversation: its messages, the consent state with the peer, and the identity. */
data class E2eChatState(
    val identity: IdentityState? = null,
    val request: E2eRequestDto? = null,
    val alarm: pm.antani.resentin.e2e.AlarmRec? = null,
)

class E2eChatViewModel(private val repo: E2eRepository, val slug: String, val peer: String) : ViewModel() {
    val messages: StateFlow<List<E2eMessageEntity>> =
        repo.messages(slug, peer).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<E2eChatState> = combine(repo.identityState, repo.requests, repo.alarms) { identity, requests, alarms ->
        E2eChatState(
            identity = identity[slug],
            request = requests[slug].orEmpty().firstOrNull { foldNick(it.peer) == foldNick(peer) },
            alarm = alarms[slug].orEmpty()[foldNick(peer)],
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), E2eChatState())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val sas: StateFlow<pm.antani.resentin.e2e.SasView?> = repo.changes
        .flatMapLatest { kotlinx.coroutines.flow.flow { emit(repo.sasView(slug, peer)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun sasStep(step: String) { viewModelScope.launch { repo.sasStep(slug, peer, step); if (keys.value != null) loadKeys() } }

    val keys = kotlinx.coroutines.flow.MutableStateFlow<Pair<E2eRepository.OwnKeyInfo?, E2eRepository.PeerKeyInfo?>?>(null)
    fun loadKeys() { viewModelScope.launch { keys.value = repo.ownKeyInfo(slug) to repo.peerKeyInfo(slug, peer) } }
    fun closeKeys() { keys.value = null }

    fun send(body: String) { if (body.isNotBlank()) viewModelScope.launch { repo.sendText(slug, peer, body.trim()) } }
    fun ask(done: (Boolean) -> Unit = {}) = viewModelScope.launch { done(repo.ask(slug, peer).isSuccess) }
    fun accept(r: E2eRequestDto) = viewModelScope.launch { repo.accept(slug, r) }
    fun refuse(r: E2eRequestDto) = viewModelScope.launch { repo.refuse(slug, r) }
    fun dismiss(r: E2eRequestDto) = viewModelScope.launch { repo.dismissRequest(slug, r) }
    fun replaceIdentity() = viewModelScope.launch { repo.replaceIdentity(slug) }
    fun refresh() = viewModelScope.launch { repo.refresh(slug) }
    fun acceptAlarm() { viewModelScope.launch { repo.acceptAlarm(slug, peer) } }
    fun markVerified() { viewModelScope.launch { repo.markVerified(slug, peer); loadKeys() } }
    fun dismissAlarm() { viewModelScope.launch { repo.dismissAlarm(slug, peer) } }

    companion object {
        fun factory(repo: E2eRepository, slug: String, peer: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = E2eChatViewModel(repo, slug, peer) as T
        }
    }
}

/** Settings → Chat cifrate: the screen-protection switch and the identity held on each network. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class E2eSettingsViewModel(private val repo: E2eRepository, private val prefs: pm.antani.resentin.data.prefs.AppPreferences) : ViewModel() {
    val secureScreen: StateFlow<Boolean> = prefs.e2eSecureScreen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setSecureScreen(v: Boolean) { viewModelScope.launch { prefs.setE2eSecureScreen(v) } }

    val identities: StateFlow<Map<String, E2eRepository.OwnKeyInfo?>> = repo.identityState
        .mapLatest { states -> states.keys.associateWith { repo.ownKeyInfo(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val states = repo.identityState

    val offer = repo.pairingOffer
    val holder = repo.holder
    val joiner = repo.joiner
    fun startHolder() { viewModelScope.launch { repo.startHolder() } }
    fun startJoiner() { repo.pairingOffer.value?.let { code -> viewModelScope.launch { repo.startJoiner(code) } } }
    fun endPairing() { viewModelScope.launch { repo.endPairing() } }

    private val _devices = kotlinx.coroutines.flow.MutableStateFlow<Map<String, List<pm.antani.resentin.e2e.E2eIdentity.OwnDevice>?>>(emptyMap())
    val devices: StateFlow<Map<String, List<pm.antani.resentin.e2e.E2eIdentity.OwnDevice>?>> = _devices
    /** Re-reads each network's device list (grappa's registry filtered by our signed list). */
    fun refreshDevices() {
        viewModelScope.launch { _devices.value = repo.identityState.value.keys.associateWith { repo.ownDevices(it) } }
    }
    fun revoke(slug: String, ed25519: String) {
        viewModelScope.launch { repo.revokeDevice(slug, ed25519); refreshDevices() }
    }

    companion object {
        fun factory(repo: E2eRepository, prefs: pm.antani.resentin.data.prefs.AppPreferences) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = E2eSettingsViewModel(repo, prefs) as T
        }
    }
}


