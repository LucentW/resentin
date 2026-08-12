package pm.antani.resentin.mirc

import org.junit.Assert.assertEquals
import org.junit.Test

class MircParserTest {

    @Test
    fun `plain text produces a single unstyled span`() {
        val spans = MircParser.parse("hello world")
        assertEquals(listOf(MircSpan("hello world")), spans)
    }

    @Test
    fun `bold toggles on and off`() {
        val input = "${Char(2)}bold${Char(2)}normal"
        val spans = MircParser.parse(input)
        assertEquals(
            listOf(
                MircSpan("bold", bold = true),
                MircSpan("normal", bold = false),
            ),
            spans,
        )
    }

    @Test
    fun `color code with foreground only`() {
        val input = "${Char(3)}4red"
        val spans = MircParser.parse(input)
        assertEquals(listOf(MircSpan("red", foreground = 4)), spans)
    }

    @Test
    fun `color code with foreground and background`() {
        val input = "${Char(3)}4,8warning"
        val spans = MircParser.parse(input)
        assertEquals(listOf(MircSpan("warning", foreground = 4, background = 8)), spans)
    }

    @Test
    fun `bare color code clears color`() {
        val input = "${Char(3)}4red${Char(3)}plain"
        val spans = MircParser.parse(input)
        assertEquals(
            listOf(
                MircSpan("red", foreground = 4),
                MircSpan("plain"),
            ),
            spans,
        )
    }

    @Test
    fun `reset clears all active styles`() {
        val input = "${Char(2)}${Char(31)}${Char(3)}4styled${Char(15)}plain"
        val spans = MircParser.parse(input)
        assertEquals(
            listOf(
                MircSpan("styled", bold = true, underline = true, foreground = 4),
                MircSpan("plain"),
            ),
            spans,
        )
    }

    @Test
    fun `underline and italic combine`() {
        val input = "${Char(31)}${Char(29)}both"
        val spans = MircParser.parse(input)
        assertEquals(listOf(MircSpan("both", underline = true, italic = true)), spans)
    }

    @Test
    fun `strikethrough toggles`() {
        val input = "${Char(30)}gone${Char(30)}back"
        val spans = MircParser.parse(input)
        assertEquals(
            listOf(
                MircSpan("gone", strikethrough = true),
                MircSpan("back", strikethrough = false),
            ),
            spans,
        )
    }

    @Test
    fun `empty string produces no spans`() {
        assertEquals(emptyList<MircSpan>(), MircParser.parse(""))
    }

    @Test
    fun `color code at end of string with no digits is a no-op reset`() {
        val input = "plain${Char(3)}"
        val spans = MircParser.parse(input)
        assertEquals(listOf(MircSpan("plain")), spans)
    }
}
