package pm.antani.resentin.ui.chat

import pm.antani.resentin.data.prefs.ReplyStyle
import pm.antani.resentin.ui.common.stripMircCodes

private const val QUOTE_PREVIEW_MAX_CHARS = 40
private const val DEFAULT_CUSTOM_TEMPLATE = "\$nick: "

private fun templateFor(style: ReplyStyle, customTemplate: String): String = when (style) {
    ReplyStyle.NICK -> "\$nick: "
    ReplyStyle.QUOTE -> "<\$nick> \$msg << "
    ReplyStyle.CUSTOM -> customTemplate.ifBlank { DEFAULT_CUSTOM_TEMPLATE }
}

/** Expands `$nick`/`$msg` in the active reply template (Settings) against the message
 * being replied to. `$msg` is the mIRC-code-stripped body, truncated to a short "quote
 * preview" length with an ellipsis when cut — enough for context without turning the
 * reply into a second copy of the original message. */
fun buildReplyPrefix(style: ReplyStyle, customTemplate: String, nick: String, messageBody: String): String {
    val plain = stripMircCodes(messageBody)
    val preview = if (plain.length > QUOTE_PREVIEW_MAX_CHARS) plain.take(QUOTE_PREVIEW_MAX_CHARS).trimEnd() + "…" else plain
    return templateFor(style, customTemplate)
        .replace("\$nick", nick)
        .replace("\$msg", preview)
}
