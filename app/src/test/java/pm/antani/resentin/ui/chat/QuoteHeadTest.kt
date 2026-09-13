package pm.antani.resentin.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuoteHeadTest {
    @Test
    fun detectsCicchettoStyleQuote() {
        val (head, rest) = splitQuoteHead("<mario> ciao mondo << risposta")
        assertEquals("<mario> ciao mondo << ", head)
        assertEquals("risposta", rest)
    }

    @Test
    fun detectsActionQuote() {
        val (head, rest) = splitQuoteHead("* mario fa cose << ah")
        assertEquals("* mario fa cose << ", head)
        assertEquals("ah", rest)
    }

    @Test
    fun detectsNickColonQuote() {
        val (head, rest) = splitQuoteHead("mario: hai visto?")
        assertEquals("mario: ", head)
        assertEquals("hai visto?", rest)
    }

    @Test
    fun rejectsBareShiftOperator() {
        // `<<` without an attribution head is prose, not a quote.
        val (head, rest) = splitQuoteHead("shift << 2 fa quattro")
        assertNull(head)
        assertEquals("shift << 2 fa quattro", rest)
    }

    @Test
    fun rejectsMidSentenceTail() {
        val (head, rest) = splitQuoteHead("dicevo che poi << boh")
        assertNull(head)
        assertEquals("dicevo che poi << boh", rest)
    }

    @Test
    fun rejectsEmptyAndPlain() {
        assertEquals(null to "", splitQuoteHead(""))
        assertEquals(null to "ciao a tutti", splitQuoteHead("ciao a tutti"))
    }
}
