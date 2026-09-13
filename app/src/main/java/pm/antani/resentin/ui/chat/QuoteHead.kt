package pm.antani.resentin.ui.chat

// Visual quote-head detection — cicchetto parity (dimmed `scrollback-reply-quote`).
//
// Matches the two built-in reply shapes at the START of a message body:
//   * `<nick> preview << ` (QUOTE template, cicchetto's own `<nick> body << `)
//   * `* nick preview << ` (same, for /me actions)
//   * `nick: ` (NICK template / classic reply-to addressing)
// A manually typed `nick: hello` dims too — per IRC convention it IS a quote,
// and the styling only ever touches the leading span, never the message itself.
// Custom templates with other shapes are not detected (unknown shape, no parse
// guessing — the #91 no-scraping rule applies to foreign text, not to our own
// two shapes).

private val QUOTE_TAIL = " << "
private const val QUOTE_HEAD_MAX_CHARS = 160

// `!addquote` archive verb (cicchetto #1107): fills the compose box and stops,
// nothing is sent. Whatever quote bot sits in the channel interprets it.
const val ADDQUOTE_COMMAND = "!addquote "

// Conservative nick head for the `nick: ` form: no leading space/</<, no
// colon or newline inside, capped length. Deliberately ASCII-ish like IRC nicks.
private val NICK_HEAD_RE = Regex("^([^\\s<>:][^:\\n]{0,30}): ")

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
            return head to text.substring(head.length)
        }
    }
    val nickMatch = NICK_HEAD_RE.find(text)
    if (nickMatch != null) {
        val head = nickMatch.value
        return head to text.substring(head.length)
    }
    return null to text
}
