package pm.antani.resentin.domain.repository

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import pm.antani.resentin.data.db.AppDatabase
import pm.antani.resentin.data.db.E2eMessageEntity
import pm.antani.resentin.domain.events.WsEvent
import pm.antani.resentin.domain.session.ConnectionManager
import pm.antani.resentin.e2e.E2eAccountStore
import pm.antani.resentin.e2e.E2eClient
import pm.antani.resentin.e2e.E2eEvent
import pm.antani.resentin.e2e.E2eIdentity
import pm.antani.resentin.e2e.IdentityState
import pm.antani.resentin.e2e.foldNick
import pm.antani.resentin.e2e.text
import pm.antani.resentin.net.auth.TokenStore
import pm.antani.resentin.net.dto.E2eRequestDto
import pm.antani.resentin.net.rest.E2eApi
import pm.antani.resentin.net.ws.SocketState

/**
 * End-to-end encrypted chats (CLIENT_PROTOCOL §4f, §4i): this device's identity per network, the
 * consent requests, and our own decrypted copy of every message (grappa keeps none, by design).
 *
 * Everything that can change the network's E2E state funnels through [refresh], one at a time per
 * network: sync the identity, read the inbox, re-read the requests. A push only says "look again".
 *
 * Not covered yet (see the engine): resolving a key alarm, SAS verification, other devices of the
 * account, channel messages, encrypted files.
 */
class E2eRepository(
    private val authRepository: AuthRepository,
    private val tokenStore: TokenStore,
    private val db: AppDatabase,
    private val store: E2eAccountStore,
) {
    private val api = { authRepository.api(E2eApi::class.java) }
    private val identity = E2eIdentity(store, api)
    private val locks = ConcurrentHashMap<String, Mutex>()

    private val _identityState = MutableStateFlow<Map<String, IdentityState>>(emptyMap())
    /** Per network slug; absent until the first sync answers. */
    val identityState: StateFlow<Map<String, IdentityState>> = _identityState.asStateFlow()

    private val _requests = MutableStateFlow<Map<String, List<E2eRequestDto>>>(emptyMap())
    val requests: StateFlow<Map<String, List<E2eRequestDto>>> = _requests.asStateFlow()

    private val _alarms = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    /** Per network slug: folded nicks whose master changed under us (a key alarm). Nothing is sent to them. */
    val alarms: StateFlow<Map<String, Set<String>>> = _alarms.asStateFlow()

    fun messages(slug: String, peer: String): Flow<List<E2eMessageEntity>> = db.e2eMessageDao().observe(slug, foldNick(peer))
    fun conversations(slug: String): Flow<List<E2eMessageEntity>> = db.e2eMessageDao().observeConversations(slug)

    fun startListening(connectionManager: ConnectionManager, scope: CoroutineScope) {
        connectionManager.events.onEach { event ->
            when (event) {
                is WsEvent.E2eActivity -> scope.launch { refresh(event.network) }
                is WsEvent.NoSuchNick -> scope.launch { absent(event.network, event.nick) }
                else -> Unit
            }
        }.launchIn(scope)
        // A push is not replayed: read everything again on every (re)connect.
        combine(tokenStore.session.filterNotNull(), connectionManager.state) { _, state -> state }
            .distinctUntilChanged()
            .onEach { state ->
                if (state != SocketState.OPEN) return@onEach
                val slugs = db.networkDao().observeNetworksWithChannels().first().map { it.network.slug }
                slugs.forEach { slug -> scope.launch { refresh(slug) } }
            }.launchIn(scope)
    }

    private fun lock(slug: String) = locks.getOrPut(slug) { Mutex() }

    private suspend fun slot(slug: String): String? {
        val session = tokenStore.session.value ?: return null
        val networkId = db.networkDao().idForSlug(slug) ?: return null
        return "${session.wsSubject}:$networkId"
    }

    private suspend fun client(slug: String): E2eClient? {
        val slot = slot(slug) ?: return null
        val nick = db.networkDao().nickForSlug(slug) ?: return null
        return E2eClient(store, api, slot, slug, { nick }) { tag ->
            tag.toLongOrNull()?.let { id -> scope.launch { db.e2eMessageDao().setStatus(id, "sent") } }
        }
    }

    // Marking a row sent happens off the call path that produced it; a scope of our own keeps that
    // from tying a callback to a caller's lifetime.
    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /** Syncs the identity, reads the inbox and the requests. Never throws: a failure is logged and the next push retries. */
    suspend fun refresh(slug: String) = lock(slug).withLock {
        runCatching {
            val slot = slot(slug) ?: return@runCatching
            val state = identity.sync(slot, slug)
            _identityState.update { it + (slug to state) }
            if (state != IdentityState.Ready) return@runCatching
            val c = client(slug) ?: return@runCatching
            apply(slug, c.drain())
            val requests = c.requests()
            _requests.update { it + (slug to requests) }
            // The peer accepted what we asked: open the chat's keys, then let the answer go.
            for (r in requests.filter { it.state == "accepted" }) {
                apply(slug, c.askKeys(r.peer))
                c.dismissRequest(r.id)
            }
        }.onFailure { Log.w(TAG, "E2E refresh failed on $slug", it) }
    }

    private suspend fun apply(slug: String, events: List<E2eEvent>) {
        for (e in events) when (e) {
            is E2eEvent.Received -> e.text()?.let { body ->
                db.e2eMessageDao().insert(E2eMessageEntity(networkSlug = slug, peer = foldNick(e.nick), peerNick = e.nick, outgoing = false, body = body, at = System.currentTimeMillis()))
            }
            is E2eEvent.KeyAlarm -> _alarms.update { it + (slug to it[slug].orEmpty() + foldNick(e.nick)) }
            is E2eEvent.SendFailed -> e.tags.forEach { tag -> tag.toLongOrNull()?.let { db.e2eMessageDao().setStatus(it, "failed") } }
            else -> Log.d(TAG, "E2E event on $slug: $e")
        }
    }

    private suspend fun absent(slug: String, nick: String) = lock(slug).withLock {
        runCatching { client(slug)?.let { apply(slug, it.absent(nick)) } }.onFailure { Log.w(TAG, "absent failed", it) }
    }

    /** Asks [peer] to start an encrypted chat (consent, §4i). */
    suspend fun ask(slug: String, peer: String): Result<Unit> = guarded(slug) { c ->
        c.askToChat(peer)
        _requests.update { it + (slug to c.requests()) }
    }

    suspend fun accept(slug: String, request: E2eRequestDto): Result<Unit> = guarded(slug) { c ->
        apply(slug, c.accept(request))
        _requests.update { it + (slug to c.requests()) }
    }

    /** Saying no needs no identity: grappa sends the REFUSE for us. */
    suspend fun refuse(slug: String, request: E2eRequestDto): Result<Unit> = runCatching {
        authRepository.api(E2eApi::class.java).refuseRequest(slug, request.id)
        _requests.update { it + (slug to it[slug].orEmpty().filter { r -> r.id != request.id }) }
    }

    suspend fun dismissRequest(slug: String, request: E2eRequestDto): Result<Unit> = runCatching {
        authRepository.api(E2eApi::class.java).dismissRequest(slug, request.id)
        _requests.update { it + (slug to it[slug].orEmpty().filter { r -> r.id != request.id }) }
    }

    /** Sends [body] encrypted to [peer]; the row shows pending until grappa takes it, failed if it cannot. */
    suspend fun sendText(slug: String, peer: String, body: String): Result<Unit> = runCatching {
        val dao = db.e2eMessageDao()
        val id = dao.insert(E2eMessageEntity(networkSlug = slug, peer = foldNick(peer), peerNick = peer, outgoing = true, body = body, at = System.currentTimeMillis(), status = "pending"))
        try {
            lock(slug).withLock {
                val c = client(slug) ?: error("no session")
                apply(slug, c.sendText(peer, body, id.toString()))
            }
        } catch (e: Exception) {
            dao.setStatus(id, "failed")
            throw e
        }
    }

    /** DESTRUCTIVE: a new identity on [slug]; every contact sees a key alarm. Explicit user action only. */
    suspend fun replaceIdentity(slug: String): Result<Unit> = runCatching {
        lock(slug).withLock {
            val slot = slot(slug) ?: error("no session")
            identity.replaceIdentity(slot, slug)
            _identityState.update { it + (slug to IdentityState.Ready) }
        }
    }

    private suspend fun guarded(slug: String, block: suspend (E2eClient) -> Unit): Result<Unit> = runCatching {
        lock(slug).withLock {
            check(_identityState.value[slug] == IdentityState.Ready) { "identity not ready" }
            block(client(slug) ?: error("no session"))
        }
    }

    private companion object {
        const val TAG = "E2eRepository"
    }
}
