package pm.antani.resentin.irc

private val CHANNEL_REFERENCE_REGEX = Regex("""(?<![\p{L}\p{N}_])#[^\s,\x00\x07:]+""")
private val TRAILING_CHANNEL_PUNCTUATION = setOf('.', ';', '!', '?', ')', ']', '}', '"', '\'')

data class ChannelReference(val range: IntRange, val channelName: String)

/** Finds clickable IRC channel references, excluding URL fragments and sentence punctuation. */
object ChannelReferenceDetector {
    fun find(text: String, visibleEndExclusive: Int = text.length): List<ChannelReference> {
        val visibleEnd = visibleEndExclusive.coerceIn(0, text.length)
        val urlRanges = UrlDetector.find(text)
        return CHANNEL_REFERENCE_REGEX.findAll(text).mapNotNull { match ->
            var end = match.range.last
            while (end >= match.range.first && text[end] in TRAILING_CHANNEL_PUNCTUATION) end--
            if (end <= match.range.first || end >= visibleEnd) {
                null
            } else {
                val range = match.range.first..end
                val overlapsUrl = urlRanges.any { urlRange ->
                    range.first <= urlRange.last && urlRange.first <= range.last
                }
                if (overlapsUrl) {
                    null
                } else {
                    ChannelReference(range, text.substring(range.first, range.last + 1))
                }
            }
        }.toList()
    }
}
