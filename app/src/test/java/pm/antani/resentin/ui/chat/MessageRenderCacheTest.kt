package pm.antani.resentin.ui.chat

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRenderCacheTest {
    @Test
    fun `deferred render strips mirc control codes even with formatting on`() {
        // stripFormatting = false (the default — colors wanted): the mid-scroll
        // fast path used to append the raw text untouched, leaving the color
        // code's own control byte and digits ("04") sitting in the
        // rendered string instead of being parsed away.
        val raw = "${Char(3)}04Hello${Char(3)} world"
        val cache = MessageRenderCache()

        val deferred = cache.messageText(raw, lightTheme = false, stripFormatting = false, deferRichContent = true)

        assertEquals("Hello world", deferred.text)
    }

    @Test
    fun `non-deferred render still applies full mirc formatting`() {
        val raw = "${Char(3)}04Hello${Char(3)} world"
        val cache = MessageRenderCache()

        val rich = cache.messageText(raw, lightTheme = false, stripFormatting = false, deferRichContent = false)

        assertEquals("Hello world", rich.text)
        assertTrue(rich.spanStyles.any { it.item.color != Color.Unspecified })
    }
}
