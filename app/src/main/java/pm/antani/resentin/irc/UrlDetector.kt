package pm.antani.resentin.irc

private val URL_REGEX = Regex("""https?://[^\s<>"']+""")

// Trailing characters that are almost always sentence/message punctuation rather than
// part of the URL itself (e.g. "vedi https://example.com." or "(https://example.com)").
private val TRAILING_NON_URL_CHARS = setOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"')

/** Finds `http(s)://` URLs in plain text, trimming trailing punctuation that's almost
 * always sentence-level rather than part of the link. Pure Kotlin — no Compose/Android
 * dependency — so both the detection logic and the Compose-facing link rendering that
 * consumes it can be tested independently. */
object UrlDetector {
    fun find(text: String): List<IntRange> = URL_REGEX.findAll(text).mapNotNull { match ->
        var end = match.range.last
        while (end >= match.range.first && text[end] in TRAILING_NON_URL_CHARS) end--
        if (end < match.range.first) null else match.range.first..end
    }.toList()
}
