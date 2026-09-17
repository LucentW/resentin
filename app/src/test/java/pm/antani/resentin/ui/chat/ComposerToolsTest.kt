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
}
