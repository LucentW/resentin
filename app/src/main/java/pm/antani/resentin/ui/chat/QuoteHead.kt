package pm.antani.resentin.ui.chat

import pm.antani.resentin.ui.common.stripMircCodes

// Visual quote-head detection — cicchetto parity (dimmed `scrollback-reply-quote`).
//
// Matches ONLY cicchetto's own QUOTE template at the START of a message body:
//   * `<nick> preview << ` (cicchetto's `<nick> body << `)
//   * `* nick preview << ` (same, for /me actions)
// A plain `nick: hello` is NOT a quote head — that shape is indistinguishable
// from ordinary prose a person typed on their own (`Nota: ...`, `Errore: ...`,
// addressing someone by name), and dimming it produced false positives on
// nearly any sentence with an early colon. Custom templates with other shapes
// are not detected either (unknown shape, no parse guessing — the #91
// no-scraping rule applies to foreign text, not to our own shape).

private val QUOTE_TAIL = " << "
private const val QUOTE_HEAD_MAX_CHARS = 160

// `!addquote` archive verb (cicchetto #1107): fills the compose box and stops,
// nothing is sent. Whatever quote bot sits in the channel interprets it.
const val ADDQUOTE_COMMAND = "!addquote "

/** Splits a leading quote head off [text]: `(head, rest)`, or `(null, text)`
 * when the body carries no recognizable reply shape. */
fun splitQuoteHead(text: String): Pair<String?, String> {
    if (text.isEmpty()) return null to text
    val tailIndex = text.indexOf(QUOTE_TAIL)
    if (tailIndex in 1..QUOTE_HEAD_MAX_CHARS) {
        val head = text.substring(0, tailIndex + QUOTE_TAIL.length)
        // The head must look like attribution, not prose that happens to hold
        // `<<` (e.g. `a << b` mid-sentence is excluded by the start anchor +
        // cap; `shift << 2` alone has no `<nick>`/`* nick` shape — require it).
        val headBody = head.dropLast(QUOTE_TAIL.length)
        if (headBody.startsWith("<") || headBody.startsWith("* ")) {
            val rest = text.substring(head.length)
            if (hasVisibleText(rest)) return head to rest
        }
    }
    return null to text
}

private fun hasVisibleText(value: String): Boolean = stripMircCodes(value).isNotBlank()
