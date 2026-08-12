package pm.antani.resentin.net.ws

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import pm.antani.resentin.net.AppJson

/**
 * Phoenix Channels wire frame: a positional 5-element JSON array
 * `[join_ref, ref, topic, event, payload]`.
 */
data class PhoenixFrame(
    val joinRef: String?,
    val ref: String?,
    val topic: String,
    val event: String,
    val payload: JsonObject,
) {
    fun encode(): String {
        val array = JsonArray(
            listOf(
                joinRef.toJsonElement(),
                ref.toJsonElement(),
                JsonPrimitive(topic),
                JsonPrimitive(event),
                payload,
            ),
        )
        return AppJson.encodeToString(JsonArray.serializer(), array)
    }

    companion object {
        fun decode(text: String): PhoenixFrame? = try {
            val array = AppJson.decodeFromString(JsonArray.serializer(), text)
            if (array.size != 5) {
                null
            } else {
                PhoenixFrame(
                    joinRef = array[0].asStringOrNull(),
                    ref = array[1].asStringOrNull(),
                    topic = array[2].jsonPrimitive.content,
                    event = array[3].jsonPrimitive.content,
                    payload = array[4] as? JsonObject ?: JsonObject(emptyMap()),
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}

private fun String?.toJsonElement(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull

private fun JsonElement.asStringOrNull(): String? =
    if (this is JsonNull) null else this.jsonPrimitive.contentOrNull
