package pm.antani.resentin.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UmodeModesTest {
    @Test
    fun fallbackCoversKnownSettableModes() {
        val toggles = availableUmodes(emptyList(), emptyList())
        val byLetter = toggles.associateBy { it.letter }
        assertTrue(byLetter.getValue("i").settable)
        assertTrue(byLetter.getValue("R").settable)
        assertFalse(byLetter.getValue("r").settable)
        assertFalse(byLetter.getValue("o").settable)
        assertFalse(byLetter.getValue("S").settable)
    }

    @Test
    fun serverSetReplacesStaticTableButKeepsActive() {
        val toggles = availableUmodes(listOf("i"), listOf("i", "x"))
        assertEquals(listOf("i", "x"), toggles.map { it.letter }.sorted())
        // Active letter omitted from the advertisement still renders.
        val withHidden = availableUmodes(listOf("w"), listOf("i"))
        assertTrue(withHidden.any { it.letter == "w" })
    }

    @Test
    fun unknownLetterIsReadOnly() {
        val toggles = availableUmodes(emptyList(), listOf("Z"))
        val z = toggles.first { it.letter == "Z" }
        assertFalse(z.settable)
        assertEquals("mode +Z", z.label)
    }
}
