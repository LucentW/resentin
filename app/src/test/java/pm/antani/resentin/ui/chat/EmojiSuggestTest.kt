package pm.antani.resentin.ui.chat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiSuggestTest {
    // The real asset, read from the source tree (a unit test's working directory is the module).
    private val catalog = EmojiCatalog.parse(File("src/main/assets/emoji/cldr.tsv").readText())

    private fun top(q: String) = searchEmoji(catalog, q).map { it.emoji }

    @Test
    fun theAssetHoldsTheCldrNamesTheIssueMeasured() {
        assertEquals(1914, catalog.size)
        assertEquals("thumbs_up", catalog.first { it.emoji == "👍" }.slug)
        assertEquals("grinning_face_with_smiling_eyes", catalog.first { it.emoji == "😄" }.slug)
        assertEquals("flag_italy", catalog.first { it.emoji == "🇮🇹" }.slug)
        assertEquals("ox", catalog.first { it.emoji == "🐂" }.slug)
    }

    @Test
    fun aColonOpensTheMenuOnlyWhenItStartsAWordAndLettersFollow() {
        assertEquals(EmojiQuery(0, 3, "sm"), emojiQueryAtCursor(":sm", 3))
        assertEquals("thum", emojiQueryAtCursor("ciao :thum", 10)!!.query)
        assertEquals(5, emojiQueryAtCursor("ciao :thum", 10)!!.start)
        assertNull("emoticon", emojiQueryAtCursor(":)", 2))
        assertNull("one letter", emojiQueryAtCursor(":D", 2))
        assertNull("a time", emojiQueryAtCursor("12:30", 5))
        assertNull("a URL", emojiQueryAtCursor("http://example", 14))
        assertNull("colon inside a word", emojiQueryAtCursor("a:bcd", 5))
        assertNull("only digits", emojiQueryAtCursor("ore :30", 7))
        assertNull("a lone colon", emojiQueryAtCursor(":", 1))
        assertNull("typed in full, left alone", emojiQueryAtCursor(":thumbs_up:", 11))
    }

    @Test
    fun theCursorDecidesWhatIsTheQuery() {
        // Editing in the middle of a message: only what is before the cursor counts.
        assertEquals("th", emojiQueryAtCursor("ciao :thumbs bella", 8)!!.query)
    }

    @Test
    fun wholeNamesAndPrefixesComeFirst() {
        assertEquals("👍", top("thumbs_up").first())
        assertEquals("🐂", top("ox").first())
        assertEquals("🇮🇹", top("flag_it").first())
        assertTrue("👍" in top("thum"))
    }

    @Test
    fun everyWordOfTheNameIsAWayIn() {
        // The issue's point: ":smile" is no prefix of 😄's slug, but its words are found. The menu shows 8, so
        // the property is checked on the whole result set; the order of the first 8 is tested below.
        fun all(q: String) = searchEmoji(catalog, q, limit = 5000).map { it.emoji }
        assertTrue("😄" in all("smiling"))
        assertTrue("😄" in all("eyes"))
        assertTrue("😄" in all("smil"))
        assertTrue("typed words, in any order", "😄" in all("smiling_eyes"))
        assertTrue("😄" in all("eyes_smiling"))
    }

    @Test
    fun theMenuShowsTheShorterNamesFirst() {
        // Of everything that has "smiling" in its name, the shortest slugs lead: what a person most likely means.
        val shown = searchEmoji(catalog, "smiling")
        assertEquals(8, shown.size)
        assertEquals(shown.sortedBy { it.slug.length }.map { it.slug.length }, shown.map { it.slug.length })
    }

    @Test
    fun noResultsIsNotAnError() {
        assertTrue(searchEmoji(catalog, "zzzzqq").isEmpty())
        assertTrue(searchEmoji(catalog, "__").isEmpty())
        assertNotNull(searchEmoji(catalog, "fa", limit = 3))
        assertEquals(3, searchEmoji(catalog, "fa", limit = 3).size)
    }
}
