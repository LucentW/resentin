package pm.antani.resentin.irc

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

private val CTCP_MARKER = Char(1)

/** A rendering-ready description of one scrollback row: either a normal chat
 * bubble (privmsg/notice/action) or a compact one-line system event
 * (join/part/quit/mode/kick/nick_change/topic). */
sealed interface FormattedEvent {
    data class Chat(val text: String, val isAction: Boolean, val isNotice: Boolean) : FormattedEvent
    data class System(val text: String) : FormattedEvent
}

/**
 * Turns a raw scrollback row (kind + sender + body + meta) into something a
 * chat UI can render directly, instead of the blank "sender, no body" rows
 * join/part/mode/kick/topic produce when shown like an ordinary message.
 * Pure Kotlin — no Android/Compose dependency — so it's unit-testable.
 */
object SystemEventFormatter {

    fun format(kind: String, sender: String, body: String?, meta: JsonObject): FormattedEvent = when (kind) {
        "join" -> FormattedEvent.System("$sender è entrato nel canale")
        "part" -> FormattedEvent.System("$sender ha lasciato il canale${reasonSuffix(body)}")
        "quit" -> FormattedEvent.System("$sender ha abbandonato IRC${reasonSuffix(body)}")
        "kick" -> {
            val target = meta.stringOrNull("target") ?: "?"
            FormattedEvent.System("$sender ha espulso $target${reasonSuffix(body)}")
        }
        "mode" -> {
            val modes = meta.stringOrNull("modes").orEmpty()
            val args = meta["args"]?.jsonArray
                ?.joinToString(" ") { it.jsonPrimitive.contentOrNull.orEmpty() }
                .orEmpty()
            val suffix = if (args.isNotBlank()) " $args" else ""
            FormattedEvent.System("$sender ha impostato modalità $modes$suffix".trimEnd())
        }
        "nick_change" -> {
            val newNick = meta.stringOrNull("new_nick") ?: "?"
            FormattedEvent.System("$sender è ora conosciuto come $newNick")
        }
        "topic" -> FormattedEvent.System("$sender ha cambiato il topic")
        "action" -> FormattedEvent.Chat(stripAction(body), isAction = true, isNotice = false)
        "notice" -> FormattedEvent.Chat(body.orEmpty(), isAction = false, isNotice = true)
        else -> FormattedEvent.Chat(body.orEmpty(), isAction = false, isNotice = false)
    }

    private fun reasonSuffix(body: String?): String = if (body.isNullOrBlank()) "" else " ($body)"

    private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    /** Strips the CTCP ACTION envelope: `ACTION text` -> `text`. */
    private fun stripAction(body: String?): String {
        if (body == null) return ""
        var text = body
        val prefix = "${CTCP_MARKER}ACTION"
        if (text.startsWith(prefix)) text = text.removePrefix(prefix)
        return text.removeSuffix(CTCP_MARKER.toString()).trim()
    }
}
