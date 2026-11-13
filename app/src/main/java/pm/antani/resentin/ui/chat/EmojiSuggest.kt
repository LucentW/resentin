package pm.antani.resentin.ui.chat

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// `:` + a few letters in the composer offers matching emoji, by CLDR name (grappa-irc issue 2663: CLDR names,
// not the Slack/GitHub set, which also turns emoticons like `:)` into emoji). The data is the CLDR short name of
// each emoji in snake_case (`thumbs_up`, `flag_italy`), from assets/emoji/cldr.tsv (scripts/gen-emoji.py).

/** One emoji: the glyph, its CLDR short name as a slug, and the name as written ("flag Italy"). */
data class EmojiEntry(val emoji: String, val slug: String, val name: String) {
    /** The words of the CLDR name: every one is a way in (":smiling" finds 😄 by its second word). */
    val words: List<String> = slug.split('_')
}

object EmojiCatalog {
    @Volatile private var cached: List<EmojiEntry>? = null

    /** `<emoji> TAB <slug> TAB <name>` per line, in CLDR order; a malformed line is skipped. */
    fun parse(tsv: String): List<EmojiEntry> = tsv.lineSequence().mapNotNull { line ->
        val f = line.split('\t')
        if (f.size == 3 && f.all { it.isNotEmpty() }) EmojiEntry(f[0], f[1], f[2]) else null
    }.toList()

    /** Loaded once, from the asset; ~2k lines. */
    fun load(context: Context): List<EmojiEntry> = cached ?: synchronized(this) {
        cached ?: parse(context.applicationContext.assets.open("emoji/cldr.tsv").bufferedReader().use { it.readText() }).also { cached = it }
    }
}

/** The `:query` under the cursor: it spans [start, end) and [query] is what follows the colon. */
data class EmojiQuery(val start: Int, val end: Int, val query: String)

/**
 * A `:query` is open at [cursor] only when the colon starts a word (at the start of the text or after whitespace) and
 * at least two characters follow it, letters, digits or `_`, one of them a letter. So `:)`, `:D`, `12:30`, `http://`,
 * `a:b` and a lone `:` never open it. A full `:smile:` is left as typed: nothing is replaced behind the user's back
 * (an open question upstream).
 */
fun emojiQueryAtCursor(text: String, cursor: Int): EmojiQuery? {
    if (cursor !in 1..text.length) return null
    val colon = text.lastIndexOf(':', startIndex = cursor - 1)
    if (colon < 0) return null
    if (colon > 0 && !text[colon - 1].isWhitespace()) return null
    val query = text.substring(colon + 1, cursor)
    if (query.length < 2 || !query.all { it.isLetterOrDigit() || it == '_' } || query.none { it.isLetter() }) return null
    return EmojiQuery(colon, cursor, query)
}

/**
 * Emoji matching [query], best first, at most [limit]. A name matches by (1) the whole slug, (2) a prefix of the slug,
 * (3) each typed word starting a word of the name, the first word first, (4) the same with the first word anywhere,
 * (5) as a substring, from three characters. Ties go to the shorter name, then to CLDR order.
 */
fun searchEmoji(entries: List<EmojiEntry>, query: String, limit: Int = 8): List<EmojiEntry> {
    val tokens = query.lowercase().split('_').filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()
    val slug = tokens.joinToString("_")
    fun tier(e: EmojiEntry): Int? = when {
        e.slug == slug -> 0
        e.slug.startsWith(slug) -> 1
        tokens.all { t -> e.words.any { it.startsWith(t) } } -> if (e.words.first().startsWith(tokens[0])) 2 else 3
        slug.length >= 3 && e.slug.contains(slug) -> 4
        else -> null
    }
    return entries.withIndex()
        .mapNotNull { (i, e) -> tier(e)?.let { Triple(it, e, i) } }
        .sortedWith(compareBy({ it.first }, { it.second.slug.length }, { it.third }))
        .take(limit)
        .map { it.second }
}

@Composable
internal fun EmojiSuggestions(
    suggestions: List<EmojiEntry>,
    selectedIndex: Int = -1,
    onSelect: (EmojiEntry) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("emoji-suggestions"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 208.dp).padding(vertical = 4.dp)) {
            itemsIndexed(suggestions, key = { _, e -> "emoji-${e.slug}" }) { index, e ->
                DropdownMenuItem(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (index == selectedIndex) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                        .testTag("emoji-${e.slug}"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    leadingIcon = { Text(e.emoji, fontSize = 22.sp) },
                    text = { Text(":${e.slug}:", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onSelect(e) },
                )
            }
        }
    }
}
