package pm.antani.resentin.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhoFlagsTest {
    private val rank = listOf("@", "%", "+")
    private val prefix = mapOf("o" to "@", "h" to "%", "v" to "+")

    @Test
    fun parseReadsTrailingGlyphAsMembership() {
        val flags = parseWhoFlags("H@", rank)
        assertFalse(flags.awayGone)
        assertFalse(flags.oper)
        assertFalse(flags.secure)
        assertEquals("@", flags.membership)
        assertTrue(flags.unknown.isEmpty())
    }

    @Test
    fun parseNeverMistakesEarlyPercentForHalfop() {
        // Oper-view +i marker in slot 2: trailing glyph is @, so membership is @.
        val flags = parseWhoFlags("H%@", rank)
        assertEquals("@", flags.membership)
    }

    @Test
    fun rosterPlainBeatsStrayPercent() {
        // Plain +i member (H%) with roster-plain: NOT halfop, IS invisible.
        val row = resolveWhoRow("H%", RosterMembership.Plain, rank)
        assertNull(row.membership)
        assertTrue(row.invisible)
    }

    @Test
    fun rosterHalfopEatsItsPercent() {
        // Real halfop (H%, roster %): membership %, NOT invisible.
        val row = resolveWhoRow("H%", RosterMembership.Sigil("%"), rank)
        assertEquals("%", row.membership)
        assertFalse(row.invisible)
    }

    @Test
    fun rosterlessLonePercentReadsAsHalfop() {
        val row = resolveWhoRow("H%", RosterMembership.NoSnapshot, rank)
        assertEquals("%", row.membership)
        assertFalse(row.invisible)
    }

    @Test
    fun invisibleHalfopKeepsBoth() {
        val row = resolveWhoRow("H%%", RosterMembership.Sigil("%"), rank)
        assertEquals("%", row.membership)
        assertTrue(row.invisible)
    }

    @Test
    fun chipsNameClassicLevels() {
        val row = ResolvedWhoRow(false, true, true, true, "@", listOf("?"))
        val labels = whoChips(row, prefix).map { it.label }
        assertEquals(listOf("here", "ircop", "invisible", "secure", "chanop", "?"), labels)
    }

    @Test
    fun membershipNamesUnknownPrefixLetter() {
        assertEquals("founder", membershipLevelName("~", mapOf("q" to "~", "o" to "@")))
        assertEquals("mode +Z", membershipLevelName("!", mapOf("Z" to "!")))
        assertEquals("~", membershipLevelName("~", prefix))
    }
}
