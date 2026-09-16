package pm.antani.resentin.domain.session

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import pm.antani.resentin.domain.events.WsEvent
import pm.antani.resentin.domain.events.WsEventDecoder
import pm.antani.resentin.net.HttpClients
import pm.antani.resentin.net.auth.TokenStore
import pm.antani.resentin.net.ws.PhoenixChannel
import pm.antani.resentin.net.ws.PhoenixSocket
import pm.antani.resentin.net.ws.ReconnectPolicy
import pm.antani.resentin.net.ws.SocketState

private const val CONNECT_TIMEOUT_MS = 15_000L

/** A joined topic's channel plus the coroutine collecting its events — paired so
 * [ConnectionManager.leaveChannel] can cancel the collector instead of leaking it once
 * the topic is left (no more "event" pushes will ever arrive for it to wake up on). */
/** Typed server refusal of an awaited verb push — [code] is the backend's own
 * error key (e.g. `not_cached`, `not_explicit`), same vocabulary cicchetto's
 * `channelPushError` surfaces, so callers can branch on it. */
class VerbException(val code: String) : Exception(code)

private class JoinedChannel(val channel: PhoenixChannel, val collectorJob: Job)

class ConnectionManager(private val tokenStore: TokenStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socket = PhoenixSocket(HttpClients.okHttpClient())
    val state: StateFlow<SocketState> = socket.state

    private val channels = mutableMapOf<String, JoinedChannel>()
    private val reconnectPolicy = ReconnectPolicy()
    private var desiredTopics: List<String> = emptyList()
    private var reconnectJob: Job? = null
    private var userInitiatedDisconnect = false

    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<WsEvent> = _events.asSharedFlow()

    init {
        scope.launch {
            state.collect { s ->
                Log.d(TAG, "state -> $s")
                when (s) {
                    SocketState.OPEN -> reconnectPolicy.reset()
                    SocketState.FAILED, SocketState.CLOSED -> if (!userInitiatedDisconnect) scheduleReconnect()
                    else -> Unit
                }
            }
        }
    }

    suspend fun connect() {
        if (state.value == SocketState.OPEN) return
        userInitiatedDisconnect = false
        val session = checkNotNull(tokenStore.session.value) { "Non autenticato" }
        socket.connect(session.host, session.token)
        withTimeout(CONNECT_TIMEOUT_MS) {
            state.first { it == SocketState.OPEN || it == SocketState.FAILED }
        }
        check(state.value == SocketState.OPEN) { "Connessione WebSocket fallita" }
    }

    fun disconnect() {
        userInitiatedDisconnect = true
        reconnectJob?.cancel()
        channels.values.forEach { it.collectorJob.cancel() }
        channels.clear()
        socket.disconnect()
    }

    /** Joins a topic if not already joined; safe to call repeatedly. Returns the join
     * reply's `response` object (e.g. a channel join's `read_cursor`/`window_counts`),
     * or null if this call was a no-op because the topic was already joined. */
    suspend fun joinChannel(topic: String): JsonObject? {
        val existing = channels[topic]
        if (existing?.channel?.joinRef != null) return null
        val channel = existing?.channel ?: PhoenixChannel(socket, topic)
        // The server's post-join snapshot (topic/modes/members "if cached") is pushed via
        // `Process.send_after(self(), {:after_join, ...}, 0)` — essentially immediately
        // after it accepts the join, often before `channel.join()` below even returns.
        // `_frames` has no replay, so a collector started AFTER join() (the previous
        // ordering) would miss any event frame that lands in that window, permanently —
        // exactly the "topic/modes/members never load" bug. Waiting for `onStart` here
        // guarantees the collector is actually attached before we send `phx_join`.
        val collectorStarted = CompletableDeferred<Unit>()
        val collectorJob = scope.launch {
            channel.events
                .onStart { collectorStarted.complete(Unit) }
                .collect { raw -> _events.emit(WsEventDecoder.decode(raw, topic)) }
        }
        channels[topic] = JoinedChannel(channel, collectorJob)
        collectorStarted.await()
        val reply = channel.join()
        return reply.payload["response"] as? JsonObject
    }

    /** Leaves a joined topic — used when the user actually parts a channel (mirrors
     * cicchetto's own "close = PART = phx_leave" behavior), not just closes its view.
     * Removed from the map BEFORE the (best-effort) leave push, so a channel re-joined
     * immediately after starts from a clean PhoenixChannel rather than reusing a stale
     * joinRef; if the leave push itself fails (e.g. the socket is already down) there's
     * nothing more to do locally — the topic dies with the connection either way. */
    suspend fun leaveChannel(topic: String) {
        val joined = channels.remove(topic) ?: return
        joined.collectorJob.cancel()
        runCatching { joined.channel.leave() }
    }

    /** Joins every topic in [topics], remembering the set so a reconnect can rejoin them
     * all. [onJoined] receives each topic's join response (null if already joined) —
     * e.g. a channel join's `read_cursor`/`window_counts` seed. */
    suspend fun joinAll(topics: List<String>, onJoined: suspend (String, JsonObject?) -> Unit = { _, _ -> }) {
        desiredTopics = topics
        // Membership changes arrive as a user-topic heartbeat. Drop subscriptions
        // that are no longer authoritative before joining the new set; otherwise a
        // kicked/parted channel would keep receiving events until reconnect.
        val desired = topics.toSet()
        channels.keys.filter { it !in desired }.toList().forEach { leaveChannel(it) }
        topics.forEach { topic -> onJoined(topic, joinChannel(topic)) }
    }

    suspend fun sendVerb(topic: String, event: String, payload: JsonObject) {
        val channel = checkNotNull(channels[topic]) { "Canale $topic non joinato" }
        channel.channel.push(event, payload)
    }

    /** #182 on grappa-irc — foreground push-suppression: reports whether this client is
     * genuinely on-screen right now, over the user-level channel (mirrors cicchetto's
     * `reportVisibility`/`visibilityHeartbeat.ts`). The server (WSPresence) trusts a
     * `visible: true` report only while FRESH and otherwise sends a push for every
     * notifiable message regardless of a live socket — without this call Resentin never
     * reports at all, so every message double-notifies (once live over the socket, once
     * again via push) whenever the WS happens to be connected. Fire-and-forget, no-op
     * before the user topic is joined (same posture as cic's). */
    suspend fun reportVisibility(subject: String, visible: Boolean) {
        sendVerb("grappa:user:$subject", "visibility", buildJsonObject { put("visible", visible) })
    }

    /** Same as [sendVerb] but awaits the `phx_reply` — for verbs whose result comes
     * back in the reply itself (`resolve_userhost`, `watchlist`, ...) rather than a
     * later broadcast. Returns the reply's `response` object on ok, throws
     * [VerbException] with the server's error code otherwise. */
    suspend fun pushVerb(topic: String, event: String, payload: JsonObject): JsonObject {
        val channel = checkNotNull(channels[topic]) { "Canale $topic non joinato" }
        val reply = channel.channel.push(event, payload).payload
        if (reply["status"]?.jsonPrimitive?.contentOrNull == "ok") {
            return reply["response"] as? JsonObject ?: JsonObject(emptyMap())
        }
        val response = reply["response"] as? JsonObject
        val code = response?.get("error")?.jsonPrimitive?.contentOrNull
            ?: response?.get("reason")?.jsonPrimitive?.contentOrNull
            ?: "verb_failed"
        throw VerbException(code)
    }

    /**
     * Retries in a loop *within a single job* until it succeeds or the user disconnects.
     * A naive "fire once, rely on the state collector to call this again on the next
     * failure" design is broken: while this job is still active (e.g. awaiting its own
     * failed [connect] call), a nested FAILED emission re-enters this function and is
     * dropped by the isActive guard below — so if that one attempt fails, nothing ever
     * schedules another. Looping internally means the guard only ever needs to reject
     * genuinely-redundant concurrent callers, not the next attempt of the same retry.
     */
    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            while (!userInitiatedDisconnect) {
                val delayMs = reconnectPolicy.nextDelayMs()
                Log.d(TAG, "reconnect attempt in ${delayMs}ms")
                delay(delayMs)
                val result = runCatching {
                    connect()
                    channels.values.forEach { it.collectorJob.cancel() }
                    channels.clear()
                    desiredTopics.forEach { joinChannel(it) }
                }
                Log.d(TAG, "reconnect result: $result")
                if (result.isSuccess) break
            }
        }
    }

    private companion object {
        const val TAG = "ConnectionManager"
    }
}
