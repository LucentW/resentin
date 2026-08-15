package pm.antani.resentin.irc

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

private val CTCP_MARKER = Char(1)

/** A rendering-ready description of one scrollback row: either a normal chat
 * bubble (privmsg/notice/action) or a compact one-line system event
 * (join/part/quit/mode/kick/nick_change/topic). System events carry structured
 * data rather than a pre-formatted string — the actual template text lives in
 * `strings.xml` via `stringResource()` in the UI layer, so this stays localizable. */
sealed interface FormattedEvent {
    data class Chat(val text: String, val isAction: Boolean, val isNotice: Boolean) : FormattedEvent

    sealed interface System : FormattedEvent {
        val sender: String

        data class Join(override val sender: String, val userHost: String? = null) : System
        data class Part(override val sender: String, val reason: String?, val userHost: String? = null) : System
        data class Quit(override val sender: String, val reason: String?, val userHost: String? = null) : System
        data class Kick(override val sender: String, val target: String, val reason: String?) : System
        data class Mode(override val sender: String, val modes: String, val args: String?) : System
        data class NickChange(override val sender: String, val newNick: String) : System
        data class TopicChanged(override val sender: String) : System
    }
}

/**
 * Turns a raw scrollback row (kind + sender + body + meta) into something a
 * chat UI can render directly, instead of the blank "sender, no body" rows
 * join/part/mode/kick/topic produce when shown like an ordinary message.
 * Pure Kotlin — no Android/Compose dependency — so it's unit-testable.
 */
object SystemEventFormatter {

    fun format(kind: String, sender: String, body: String?, meta: JsonObject): FormattedEvent = when (kind) {
        "join" -> FormattedEvent.System.Join(sender, userHost(meta))
        "part" -> FormattedEvent.System.Part(sender, body?.takeIf { it.isNotBlank() }, userHost(meta))
        "quit" -> FormattedEvent.System.Quit(sender, body?.takeIf { it.isNotBlank() }, userHost(meta))
        "kick" -> FormattedEvent.System.Kick(
            sender,
            meta.stringOrNull("target") ?: "?",
            body?.takeIf { it.isNotBlank() },
        )
        "mode" -> {
            val modes = meta.stringOrNull("modes").orEmpty()
            val args = meta["args"]?.jsonArray
                ?.joinToString(" ") { it.jsonPrimitive.contentOrNull.orEmpty() }
                .orEmpty()
            FormattedEvent.System.Mode(sender, modes, args.takeIf { it.isNotBlank() })
        }
        "nick_change" -> FormattedEvent.System.NickChange(sender, meta.stringOrNull("new_nick") ?: "?")
        "topic" -> FormattedEvent.System.TopicChanged(sender)
        "action" -> FormattedEvent.Chat(stripAction(body), isAction = true, isNotice = false)
        "notice" -> FormattedEvent.Chat(body.orEmpty(), isAction = false, isNotice = true)
        else -> FormattedEvent.Chat(body.orEmpty(), isAction = false, isNotice = false)
    }

    private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    /** `sender_user`/`sender_host` ride together or not at all (see
     * `Grappa.Scrollback.Meta`'s per-kind doc) — only join/part/quit ever carry them. */
    private fun userHost(meta: JsonObject): String? {
        val user = meta.stringOrNull("sender_user") ?: return null
        val host = meta.stringOrNull("sender_host") ?: return null
        return "$user@$host"
    }

    /** Strips the CTCP ACTION envelope: `ACTION text` -> `text`. */
    private fun stripAction(body: String?): String {
        if (body == null) return ""
        var text = body
        val prefix = "${CTCP_MARKER}ACTION"
        if (text.startsWith(prefix)) text = text.removePrefix(prefix)
        return text.removeSuffix(CTCP_MARKER.toString()).trim()
    }
}
