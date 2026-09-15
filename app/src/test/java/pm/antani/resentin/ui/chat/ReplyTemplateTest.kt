package pm.antani.resentin.ui.chat

import org.junit.Assert.assertEquals
import pm.antani.resentin.data.prefs.ReplyStyle
import org.junit.Test

class ReplyTemplateTest {
    @Test
    fun quoteStyleWrapsAPlainMessage() {
        val prefix = buildReplyPrefix(ReplyStyle.QUOTE, "", "mario", "ciao a tutti")
        assertEquals("<mario> ciao a tutti << ", prefix)
    }

    @Test
    fun quoteStyleCollapsesAnAlreadyQuotedMessage() {
        // Replying to a message that is itself a reply must not nest a second
        // <nick> attribution — it should quote the ANSWER, not the whole chain.
        val prefix = buildReplyPrefix(ReplyStyle.QUOTE, "", "nick1", "<nick2> messaggio << risposta")
        assertEquals("<nick1> risposta << ", prefix)
    }

    @Test
    fun quoteStyleCollapsesAnAlreadyQuotedActionMessage() {
        val prefix = buildReplyPrefix(ReplyStyle.QUOTE, "", "nick1", "* nick2 fa cose << risposta")
        assertEquals("<nick1> risposta << ", prefix)
    }

    @Test
    fun nickStyleIgnoresTheMessageBody() {
        val prefix = buildReplyPrefix(ReplyStyle.NICK, "", "mario", "<nick2> messaggio << risposta")
        assertEquals("mario: ", prefix)
    }

    @Test
    fun customTemplateAlsoCollapsesNestedQuotes() {
        val prefix = buildReplyPrefix(ReplyStyle.CUSTOM, "[\$nick] \$msg", "nick1", "<nick2> messaggio << risposta")
        assertEquals("[nick1] risposta", prefix)
    }
}
