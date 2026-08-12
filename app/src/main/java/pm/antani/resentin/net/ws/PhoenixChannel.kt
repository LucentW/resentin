package pm.antani.resentin.net.ws

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject

class PhoenixChannel(private val socket: PhoenixSocket, val topic: String) {

    var joinRef: String? = null
        private set

    val events: Flow<JsonObject> = socket.frames
        .filter { it.topic == topic && it.event == "event" }
        .map { it.payload }

    suspend fun join(): PhoenixFrame {
        val reply = socket.push(joinRef = null, topic = topic, event = "phx_join", payload = JsonObject(emptyMap()))
        joinRef = reply.joinRef
        return reply
    }

    suspend fun push(event: String, payload: JsonObject): PhoenixFrame {
        val currentJoinRef = checkNotNull(joinRef) { "Canale $topic non joinato" }
        return socket.push(joinRef = currentJoinRef, topic = topic, event = event, payload = payload)
    }
}
