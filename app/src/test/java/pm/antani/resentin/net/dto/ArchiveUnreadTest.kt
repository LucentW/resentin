package pm.antani.resentin.net.dto

import org.junit.Assert.assertEquals
import org.junit.Test
import pm.antani.resentin.net.AppJson

class ArchiveUnreadTest {
    @Test
    fun `old server payload without unread fields decodes with zero defaults`() {
        val envelope = AppJson.decodeFromString<ArchiveEnvelopeDto>(
            """
            {"archive": [
              {"target": "#foo", "kind": "channel", "last_activity": 1700000000000, "row_count": 12},
              {"target": "Alice", "kind": "query", "last_activity": 1700000001000, "row_count": 3}
            ]}
            """.trimIndent(),
        )
        assertEquals(2, envelope.archive.size)
        assertEquals(0, envelope.archive[0].unreadMessages)
        assertEquals(0, envelope.archive[0].unreadMentions)
        assertEquals("none", envelope.archive[0].severity)
    }

    @Test
    fun `new server payload with unread fields decodes`() {
        val envelope = AppJson.decodeFromString<ArchiveEnvelopeDto>(
            """
            {"archive": [
              {"target": "#foo", "kind": "channel", "last_activity": 1700000000000,
               "row_count": 12, "unread_messages": 5, "unread_mentions": 1, "severity": "mention"}
            ]}
            """.trimIndent(),
        )
        assertEquals(5, envelope.archive[0].unreadMessages)
        assertEquals(1, envelope.archive[0].unreadMentions)
        assertEquals("mention", envelope.archive[0].severity)
    }

    @Test
    fun `merge prefers me snapshot and folds target case`() {
        val entries = listOf(
            ArchiveEntryDto("#Foo", "channel", 1L, 10),
            ArchiveEntryDto("BOB", "query", 2L, 4),
            ArchiveEntryDto("#quiet", "channel", 3L, 7),
        )
        val unread = mapOf(
            "#foo" to UnreadCountDto(messages = 5, mentions = 1, severity = "mention"),
            "bob" to UnreadCountDto(messages = 2, mentions = 0, severity = "message"),
        )
        val merged = mergeArchiveUnread(entries, unread)
        assertEquals(5, merged[0].unreadMessages)
        assertEquals(1, merged[0].unreadMentions)
        assertEquals("mention", merged[0].severity)
        assertEquals(2, merged[1].unreadMessages)
        // No /me row: falls back to the payload's own fields (zero here).
        assertEquals(0, merged[2].unreadMessages)
    }

    @Test
    fun `rollup sums messages and mentions separately`() {
        val merged = listOf(
            ArchiveEntryWithUnread(ArchiveEntryDto("#a", "channel", 1L, 1), unreadMessages = 5, unreadMentions = 1),
            ArchiveEntryWithUnread(ArchiveEntryDto("b", "query", 2L, 1), unreadMessages = 2, unreadMentions = 0),
        )
        assertEquals(7, merged.unreadMessagesRollup())
        assertEquals(1, merged.unreadMentionsRollup())
        assertEquals(0, emptyList<ArchiveEntryWithUnread>().unreadMessagesRollup())
    }
}
