package pm.antani.resentin.ui.chat

import pm.antani.resentin.data.prefs.ReplyStyle
import pm.antani.resentin.ui.common.stripMircCodes

private const val QUOTE_PREVIEW_MAX_CHARS = 40
private const val DEFAULT_CUSTOM_TEMPLATE = "\$nick: "

/** Plain visual preview for the composer reply bar. IRC formatting is removed while
 * line breaks are preserved, so Compose can apply its two-line ellipsis at layout time. */
fun buildReplyPreview(messageBody: String): String = messageBody
    .let(::stripMircCodes)
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .split('\n')
    .joinToString("\n") { it.trimEnd() }
    .trim()

private fun templateFor(style: ReplyStyle, customTemplate: String): String = when (style) {
    ReplyStyle.NICK -> "\$nick: "
    ReplyStyle.QUOTE -> "<\$nick> \$msg << "
    ReplyStyle.CUSTOM -> customTemplate.ifBlank { DEFAULT_CUSTOM_TEMPLATE }
}

/** Expands `$nick`/`$msg` in the active reply template (Settings) against the message
 * being replied to. `$msg` is the mIRC-code-stripped body, truncated to a short "quote
 * preview" length with an ellipsis when cut — enough for context without turning the
 * reply into a second copy of the original message.
 *
 * If that body is ITSELF a quote head (replying to a reply), `$msg` collapses to just
 * the quoted answer instead of the whole nested attribution — `<nick2> msg << answer`
 * becomes `answer`, so replying again produces `<nick1> answer << `, not
 * `<nick1> <nick2> msg << answer << ` growing one `<nick>` deeper on every hop. */
fun buildReplyPrefix(style: ReplyStyle, customTemplate: String, nick: String, messageBody: String): String {
    val plain = stripMircCodes(messageBody)
    val (_, collapsed) = splitQuoteHead(plain)
    val preview = if (collapsed.length > QUOTE_PREVIEW_MAX_CHARS) collapsed.take(QUOTE_PREVIEW_MAX_CHARS).trimEnd() + "…" else collapsed
    return templateFor(style, customTemplate)
        .replace("\$nick", nick)
        .replace("\$msg", preview)
}
