package pm.antani.resentin.ui.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MircComposerVisualTransformationTest {
    @Test
    fun `mirc styles are visible while control codes stay hidden`() {
        val source = "${Char(29)}italic${Char(29)} ${Char(31)}underlined${Char(31)}"

        val result = MircComposerVisualTransformation.filter(AnnotatedString(source))

        assertEquals("italic underlined", result.text.text)
        assertTrue(result.text.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(result.text.spanStyles.any { it.item.textDecoration == TextDecoration.Underline })
    }

    @Test
    fun `offset mapping keeps cursor after hidden style codes`() {
        val source = "${Char(29)}text${Char(29)}"
        val result = MircComposerVisualTransformation.filter(AnnotatedString(source))

        assertEquals(0, result.offsetMapping.originalToTransformed(1))
        assertEquals(1, result.offsetMapping.transformedToOriginal(0))
        assertEquals(source.length, result.offsetMapping.transformedToOriginal(result.text.length))
    }
}
