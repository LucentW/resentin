package pm.antani.resentin.ui.chat

import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import java.util.Locale

internal data class EmojiSearchEntry(
    val emoji: String,
    val name: String,
)

internal data class EmojiQuery(
    val start: Int,
    val end: Int,
    val query: String,
)

/** Builds search labels from the Unicode emoji data available on the device. */
internal fun systemEmojiSearchEntries(): List<EmojiSearchEntry> {
    val result = linkedMapOf<String, EmojiSearchEntry>()
    val ranges = listOf(0x00A9..0x00AE, 0x203C..0x3299, 0x1F000..0x1FAFF)

    for (range in ranges) {
        for (codePoint in range) {
            if (!UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI)) continue
            if (UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI_MODIFIER)) continue

            val emoji =
                String(Character.toChars(codePoint)) +
                    if (UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI_PRESENTATION)) "" else "\uFE0F"
            val name = UCharacter.getName(codePoint)?.let(::canonicalEmojiName) ?: continue
            result.putIfAbsent(emoji, EmojiSearchEntry(emoji, name))

            if (UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI_MODIFIER_BASE)) {
                for (tone in 0x1F3FB..0x1F3FF) {
                    val toned = emoji.trimEnd('\uFE0F') + String(Character.toChars(tone))
                    result.putIfAbsent(toned, EmojiSearchEntry(toned, name))
                }
            }
        }
    }

    result["#️⃣"] = EmojiSearchEntry("#️⃣", "hash keycap")
    result["*️⃣"] = EmojiSearchEntry("*️⃣", "asterisk keycap")
    for (digit in '0'..'9') {
        val emoji = "$digit\uFE0F\u20E3"
        result[emoji] = EmojiSearchEntry(emoji, "$digit keycap")
    }

    Locale.getISOCountries().forEach { country ->
        val flag = country.map { letter -> String(Character.toChars(0x1F1E6 + letter.code - 65)) }.joinToString("")
        val name = Locale.Builder().setRegion(country).build().displayCountry
        if (name.isNotBlank()) result.putIfAbsent(flag, EmojiSearchEntry(flag, canonicalEmojiName("$name flag")))
    }

    return result.values.toList()
}

internal fun searchSystemEmojis(
    entries: List<EmojiSearchEntry>,
    query: String,
    limit: Int = 8,
): List<EmojiSearchEntry> {
    val needle = canonicalEmojiName(query.removePrefix(":"))
    if (needle.isEmpty() || limit <= 0) return emptyList()

    return entries
        .asSequence()
        .mapNotNull { entry ->
            val name = canonicalEmojiName(entry.name)
            val score = when {
                name == needle -> 0
                name.startsWith(needle) -> 1
                name.split('_').any { it.startsWith(needle) } -> 2
                name.contains(needle) -> 3
                else -> return@mapNotNull null
            }
            score to entry
        }
        .sortedWith(compareBy<Pair<Int, EmojiSearchEntry>> { it.first }.thenBy { it.second.name })
        .map { it.second }
        .take(limit)
        .toList()
}

internal fun activeEmojiQuery(value: androidx.compose.ui.text.input.TextFieldValue): EmojiQuery? {
    if (!value.selection.collapsed) return null
    val cursor = value.selection.start.coerceIn(0, value.text.length)
    val tokenStart = value.text.lastIndexOfAny(charArrayOf(' ', '\n', '\t'), cursor - 1) + 1
    if (tokenStart >= cursor || value.text.getOrNull(tokenStart) != ':') return null

    val query = value.text.substring(tokenStart + 1, cursor)
    if (query.isEmpty() || query.any { !it.isLetterOrDigit() && it != '_' && it != '-' }) return null
    return EmojiQuery(tokenStart, cursor, query)
}

internal fun replaceEmojiQuery(
    value: androidx.compose.ui.text.input.TextFieldValue,
    query: EmojiQuery,
    emoji: String,
): androidx.compose.ui.text.input.TextFieldValue {
    val text = value.text.replaceRange(query.start, query.end, emoji)
    return value.copy(text = text, selection = androidx.compose.ui.text.TextRange(query.start + emoji.length))
}

private fun canonicalEmojiName(value: String): String {
    val result = StringBuilder(value.length)
    var separatorPending = false
    value.lowercase(Locale.ROOT).forEach { character ->
        if (character.isLetterOrDigit()) {
            if (separatorPending && result.isNotEmpty()) result.append('_')
            result.append(character)
            separatorPending = false
        } else if (result.isNotEmpty()) {
            separatorPending = true
        }
    }
    return result.toString()
}
