package pm.antani.resentin.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pm.antani.resentin.data.db.MessageEntity

class ChatTimelineTest {
    @Test
    fun repeatedJoinAndLeaveEventsBecomeOneBurst() {
        val rows = buildChatTimeline(
            listOf(event(1, "join", 0), event(2, "part", 5_000), event(3, "join", 10_000), event(4, "quit", 15_000)),
            readCursor = null,
        )

        assertEquals(1, rows.size)
        val burst = rows.single() as ChatTimelineRow.PresenceBurst
        assertEquals(2, burst.joins)
        assertEquals(2, burst.leaves)
        assertEquals(listOf(1L, 2L, 3L, 4L), burst.messages.map { it.id })
    }

    @Test
    fun ordinaryJoinAndPartRemainSeparate() {
        val rows = buildChatTimeline(listOf(event(1, "join", 0), event(2, "part", 5_000)), readCursor = null)

        assertEquals(2, rows.size)
        assertEquals(listOf("join", "part"), rows.map { (it as ChatTimelineRow.Message).message.kind })
    }

    @Test
    fun otherMessagesBreakBurst() {
        val rows = buildChatTimeline(
            listOf(
                event(1, "join", 0), event(2, "part", 5_000),
                event(3, "privmsg", 6_000, sender = "other"),
                event(4, "join", 10_000), event(5, "part", 15_000),
            ),
            readCursor = null,
        )

        assertEquals(5, rows.size)
        assertTrueAllMessages(rows)
    }

    @Test
    fun eventsAcrossReadDividerAreNotMerged() {
        val rows = buildChatTimeline(
            listOf(event(1, "join", 0), event(2, "part", 5_000), event(3, "join", 10_000), event(4, "quit", 15_000)),
            readCursor = 2,
        )

        assertEquals(4, rows.size)
        assertTrueAllMessages(rows)
    }

    @Test
    fun distantEventsAreNotMerged() {
        val rows = buildChatTimeline(
            listOf(event(1, "join", 0), event(2, "part", 40_000), event(3, "join", 80_000)),
            readCursor = null,
        )

        assertEquals(3, rows.size)
        assertTrueAllMessages(rows)
    }

    @Test
    fun smartFilterSummarizesRepeatedQuietPresenceChanges() {
        val rows = buildChatTimeline(
            listOf(event(1, "join", 0), event(2, "part", 5_000), event(3, "join", 10_000), event(4, "quit", 15_000)),
            readCursor = null,
            smartPresenceFilterEnabled = true,
        )

        val summary = rows.single() as ChatTimelineRow.SuppressedPresence
        assertEquals("Nick", summary.sender)
        assertEquals(2, summary.joins)
        assertEquals(2, summary.leaves)
        assertEquals(listOf(1L, 2L, 3L, 4L), summary.messages.map { it.id })
    }

    @Test
    fun smartFilterHidesAnIsolatedQuietJoin() {
        val rows = buildChatTimeline(
            listOf(event(1, "join", 0)),
            readCursor = null,
            smartPresenceFilterEnabled = true,
        )

        assertTrue(rows.isEmpty())
    }

    @Test
    fun smartFilterKeepsPresenceForSomeoneWhoRecentlySpoke() {
        val rows = buildChatTimeline(
            listOf(
                event(1, "privmsg", 0),
                event(2, "join", 60_000),
                event(3, "part", 65_000),
                event(4, "join", 70_000),
            ),
            readCursor = null,
            smartPresenceFilterEnabled = true,
        )

        val summary = rows.last() as ChatTimelineRow.PresenceBurst
        assertEquals(listOf(2L, 3L, 4L), summary.messages.map { it.id })
    }

    @Test
    fun smartFilterNeverHidesOwnPresenceOrImportantEvents() {
        val rows = buildChatTimeline(
            listOf(
                event(1, "join", 0, sender = "me"),
                event(2, "join", 5_000, sender = "quiet"),
                event(3, "kick", 6_000, sender = "operator"),
            ),
            readCursor = null,
            smartPresenceFilterEnabled = true,
            alwaysVisibleSender = "Me",
        )

        assertEquals(listOf(1L, 3L), rows.flatMap { it.messages }.map { it.id })
    }

    private fun assertTrueAllMessages(rows: List<ChatTimelineRow>) {
        assertEquals(rows.size, rows.filterIsInstance<ChatTimelineRow.Message>().size)
    }

    private fun event(id: Long, kind: String, time: Long, sender: String = "Nick") = MessageEntity(
        networkSlug = "test",
        channelName = "#test",
        id = id,
        serverTime = time,
        kind = kind,
        sender = sender,
        body = null,
    )
}
