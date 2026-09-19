package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ScrollbackMessageDto(
    val id: Long,
    val network: String,
    val channel: String,
    val serverTime: Long,
    val kind: String,
    val sender: String,
    val body: String? = null,
    val meta: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class MessageEventPayloadDto(
    val message: ScrollbackMessageDto,
)

@Serializable
data class SendMessageDto(
    val body: String,
    /** Optional CTCP relay target for control queries such as /ctcp or /ping. */
    val ctcpTarget: String? = null,
)
