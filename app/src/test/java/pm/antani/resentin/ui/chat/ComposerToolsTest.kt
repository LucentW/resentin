package pm.antani.resentin.ui.chat

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerToolsTest {
    @Test
    fun `style wraps selected text and preserves selection`() {
        val value = TextFieldValue("hello", TextRange(0, 5))

        val result = toggleIrcStyle(value, codePoint = 2)

        assertEquals("\u0002hello\u0002", result.text)
        assertEquals(TextRange(1, 6), result.selection)
    }

    @Test
    fun `insert replaces selected text and places cursor after insertion`() {
        val value = TextFieldValue("hello", TextRange(1, 4))

        val result = insertComposerText(value, "🙂")

        assertEquals("h🙂o", result.text)
        assertEquals(TextRange(3), result.selection)
    }

    @Test
    fun `apply colors supports foreground and background`() {
        val value = TextFieldValue("hello", TextRange(0, 5))

        val result = applyIrcColors(value, foreground = 42, background = 98)

        assertEquals("\u000342,98hello\u000f", result.text)
        assertEquals(TextRange(12), result.selection)
    }

    @Test
    fun `remove colors preserves other formatting`() {
        val value = TextFieldValue("\u000342,98hello\u0002", TextRange(0, 12))

        val result = clearIrcColors(value)

        assertEquals("hello\u0002", result.text)
        assertEquals(TextRange(6), result.selection)
    }
}
