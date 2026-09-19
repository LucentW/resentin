package pm.antani.resentin.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pm.antani.resentin.data.db.MessageEntity

class ChatJumpResolverTest {
    private fun msg(id: Long, time: Long) = MessageEntity(
        networkSlug = "n",
        channelName = "#c",
        id = id,
        serverTime = time,
        kind = "privmsg",
        sender = "nick",
        body = "x",
    )

    @Test
    fun landsOnFirstRowAtOrAfterTime() {
        val rows = listOf(msg(1, 100), msg(2, 200), msg(3, 300))
        assertEquals(2L, ChatJumpResolver.firstAtOrAfterTime(rows, 150)?.id)
        assertEquals(2L, ChatJumpResolver.firstAtOrAfterTime(rows, 200)?.id)
    }

    @Test
    fun sameServerTimeTieBreaksOnSmallestId() {
        val rows = listOf(msg(9, 200), msg(3, 200), msg(5, 200))
        assertEquals(3L, ChatJumpResolver.firstAtOrAfterTime(rows, 200)?.id)
    }

    @Test
    fun noRowAtOrAfterTimeIsNull() {
        assertNull(ChatJumpResolver.firstAtOrAfterTime(listOf(msg(1, 100)), 999))
        assertNull(ChatJumpResolver.firstAtOrAfterTime(emptyList(), 10))
    }

    @Test
    fun rowIndexForIdFindsContainingRow() {
        val rows = listOf(
            ChatTimelineRow.Message(msg(1, 100)),
            ChatTimelineRow.Message(msg(2, 200)),
        )
        assertEquals(1, ChatJumpResolver.rowIndexForId(rows, 2))
        assertEquals(-1, ChatJumpResolver.rowIndexForId(rows, 42))
    }
}
