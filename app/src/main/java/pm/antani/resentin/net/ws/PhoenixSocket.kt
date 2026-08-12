package pm.antani.resentin.net.ws

import android.util.Log
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

private const val HEARTBEAT_INTERVAL_MS = 28_000L
private const val PUSH_TIMEOUT_MS = 15_000L

class PhoenixSocket(private val okHttpClient: OkHttpClient) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private val refCounter = AtomicInteger(0)
    private val pendingReplies = mutableMapOf<String, CompletableDeferred<PhoenixFrame>>()
    private var heartbeatJob: Job? = null

    private val _state = MutableStateFlow(SocketState.IDLE)
    val state: StateFlow<SocketState> = _state.asStateFlow()

    private val _frames = MutableSharedFlow<PhoenixFrame>(extraBufferCapacity = 64)
    val frames: SharedFlow<PhoenixFrame> = _frames.asSharedFlow()

    fun nextRef(): String = refCounter.incrementAndGet().toString()

    fun connect(host: String, token: String) {
        if (_state.value == SocketState.CONNECTING || _state.value == SocketState.OPEN) return
        _state.value = SocketState.CONNECTING

        val tokenB64 = Base64.getUrlEncoder().encodeToString(token.toByteArray())
        val request = Request.Builder()
            .url("wss://$host/socket/websocket?client_proto=1&vsn=2.0.0")
            .header("Sec-WebSocket-Protocol", "phoenix, base64url.bearer.phx.$tokenB64")
            .build()

        webSocket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "onOpen")
                    _state.value = SocketState.OPEN
                    startHeartbeat()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    PhoenixFrame.decode(text)?.let(::handleFrame)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "onClosed code=$code reason=$reason")
                    _state.value = SocketState.CLOSED
                    stopHeartbeat()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.d(TAG, "onFailure", t)
                    _state.value = SocketState.FAILED
                    stopHeartbeat()
                }
            },
        )
    }

    fun disconnect() {
        stopHeartbeat()
        webSocket?.close(1000, "bye")
        webSocket = null
        _state.value = SocketState.CLOSED
    }

    suspend fun push(joinRef: String?, topic: String, event: String, payload: JsonObject): PhoenixFrame {
        val ref = nextRef()
        val deferred = CompletableDeferred<PhoenixFrame>()
        pendingReplies[ref] = deferred

        val frame = PhoenixFrame(joinRef = joinRef ?: ref, ref = ref, topic = topic, event = event, payload = payload)
        val socket = webSocket
        if (socket == null) {
            pendingReplies.remove(ref)
            throw IllegalStateException("Socket non connesso")
        }
        socket.send(frame.encode())
        try {
            return withTimeout(PUSH_TIMEOUT_MS) { deferred.await() }
        } catch (e: Exception) {
            Log.d(TAG, "push timeout/failure for $event ref=$ref", e)
            pendingReplies.remove(ref)
            // A reply that never arrives means the connection is dead even if OkHttp
            // hasn't noticed yet (e.g. a silently dropped network). cancel() tears down
            // the socket but doesn't reliably invoke onFailure/onClosed, so flip the
            // state here explicitly rather than leaving a zombie "OPEN" status that
            // never triggers a reconnect.
            webSocket?.cancel()
            webSocket = null
            _state.value = SocketState.FAILED
            stopHeartbeat()
            throw e
        }
    }

    private fun handleFrame(frame: PhoenixFrame) {
        val ref = frame.ref
        if (ref != null && frame.event == "phx_reply") {
            pendingReplies.remove(ref)?.complete(frame)
        }
        scope.launch { _frames.emit(frame) }
    }

    private fun startHeartbeat() {
        heartbeatJob = scope.launch {
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS)
                runCatching { push(joinRef = null, topic = "phoenix", event = "heartbeat", payload = JsonObject(emptyMap())) }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private companion object {
        const val TAG = "PhoenixSocket"
    }
}
