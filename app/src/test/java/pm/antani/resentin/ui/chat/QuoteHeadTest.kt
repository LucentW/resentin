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
    fun rejectsPlainNickColonAddressing() {
        // Not a quote head: indistinguishable from ordinary prose with an
        // early colon (`Nota: ...`, `Errore: ...`, addressing someone by name).
        val text = "mario: hai visto?"
        val (head, rest) = splitQuoteHead(text)
        assertNull(head)
        assertEquals(text, rest)
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
    fun rejectsQuoteAttributionWithoutReplyText() {
        val text = "<mario> ciao mondo << "
        val (head, rest) = splitQuoteHead(text)
        assertNull(head)
        assertEquals(text, rest)
    }

    @Test
    fun rejectsEmptyAndPlain() {
        assertEquals(null to "", splitQuoteHead(""))
        assertEquals(null to "ciao a tutti", splitQuoteHead("ciao a tutti"))
    }

    @Test
    fun parsesNickAndPreviewFromBracketHead() {
        val parts = quoteHeadParts("<mario> ciao mondo << ")
        assertEquals(QuoteHeadParts("mario", "ciao mondo", isAction = false), parts)
    }

    @Test
    fun parsesNickAndPreviewFromActionHead() {
        val parts = quoteHeadParts("* mario fa cose << ")
        assertEquals(QuoteHeadParts("mario", "fa cose", isAction = true), parts)
    }

    @Test
    fun rejectsHeadWithoutTail() {
        assertNull(quoteHeadParts("<mario> ciao mondo"))
    }
}
