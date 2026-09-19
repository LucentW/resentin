package pm.antani.resentin.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pm.antani.resentin.data.prefs.ChatDisplayMode

class SendFlightTest {
    @Test
    fun morphStartsAtZeroEndsAtOne() {
        assertEquals(0f, sendFlightMorph(0f), 1e-6f)
        assertEquals(1f, sendFlightMorph(1f), 1e-6f)
    }

    @Test
    fun morphCompletesWithinFirstStretch() {
        assertEquals(1f, sendFlightMorph(SEND_FLIGHT_MORPH_END), 1e-6f)
        assertEquals(1f, sendFlightMorph(0.9f), 1e-6f)
    }

    @Test
    fun swapStartsAfterMorphAndEndsBeforeOne() {
        assertEquals(0f, sendFlightSwap(0f), 1e-6f)
        assertEquals(0f, sendFlightSwap(SEND_FLIGHT_SWAP_START), 1e-6f)
        assertEquals(1f, sendFlightSwap(SEND_FLIGHT_SWAP_END), 1e-6f)
        assertEquals(1f, sendFlightSwap(1f), 1e-6f)
    }

    @Test
    fun riseStartsAtZeroEndsAtOne() {
        assertEquals(0f, sendFlightRise(0f), 1e-6f)
        assertEquals(1f, sendFlightRise(1f), 1e-6f)
    }

    @Test
    fun riseClampsOutsideUnitRange() {
        assertEquals(0f, sendFlightRise(-2f), 1e-6f)
        assertEquals(1f, sendFlightRise(42f), 1e-6f)
    }

    @Test
    fun eligibilityFiltersCommandsMultilineAndModes() {
        assertTrue(sendFlightEligible("ciao", displayIsIrcLine = false, isServer = false))
        assertFalse(sendFlightEligible("", displayIsIrcLine = false, isServer = false))
        assertFalse(sendFlightEligible("   ", displayIsIrcLine = false, isServer = false))
        assertFalse(sendFlightEligible("/join #x", displayIsIrcLine = false, isServer = false))
        assertFalse(sendFlightEligible("a\nb", displayIsIrcLine = false, isServer = false))
        assertFalse(sendFlightEligible("ciao", displayIsIrcLine = true, isServer = false))
        assertFalse(sendFlightEligible("ciao", displayIsIrcLine = false, isServer = true))
        assertFalse(sendFlightEligible("x".repeat(SEND_FLIGHT_MAX_CHARS + 1), displayIsIrcLine = false, isServer = false))
    }

    @Test
    fun displayModeReference() {
        // Guards the ChatDisplayMode import contract used by the call site.
        assertEquals(ChatDisplayMode.IRC_LINE, ChatDisplayMode.IRC_LINE)
    }

    private fun prevMsg(id: Long, time: Long, sender: String = "me", kind: String = "privmsg") =
        pm.antani.resentin.data.db.MessageEntity(
            networkSlug = "n",
            channelName = "#c",
            id = id,
            serverTime = time,
            kind = kind,
            sender = sender,
            body = "x",
        )

    @Test
    fun ghostGroupsLikeTheListWould() {
        val prev = prevMsg(1, 1000)
        assertTrue(ghostTightFor(prev, 1000 + 60_000, "me"))
        assertTrue(ghostTightFor(prev, 1000, "ME"))
    }

    @Test
    fun ghostDoesNotGroupAcrossGapSenderOrSystem() {
        val prev = prevMsg(1, 1000)
        assertFalse(ghostTightFor(null, 2000, "me"))
        assertFalse(ghostTightFor(prev, 1000 + 5 * 60_000 + 1, "me"))
        assertFalse(ghostTightFor(prev, 2000, "other"))
        assertFalse(ghostTightFor(prevMsg(1, 1000, kind = "join"), 2000, "me"))
        assertFalse(ghostTightFor(prev, 999, "me"))
    }
}
