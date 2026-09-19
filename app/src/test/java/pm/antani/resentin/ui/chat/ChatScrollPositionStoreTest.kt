package pm.antani.resentin.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pm.antani.resentin.data.db.MessageEntity

class ChatScrollPositionStoreTest {
    private fun msg(id: Long) = MessageEntity(
        networkSlug = "n",
        channelName = "#c",
        id = id,
        serverTime = id * 1000,
        kind = "privmsg",
        sender = "nick",
        body = "x",
    )

    @Test
    fun anchorIndexWithoutDividerCountsFromTail() {
        val rows = listOf(
            ChatTimelineRow.Message(msg(1)),
            ChatTimelineRow.Message(msg(2)),
            ChatTimelineRow.Message(msg(3)),
        )
        // reverse-layout: newest (id 3) = index 0, oldest (id 1) = index 2.
        assertEquals(0, anchorListIndex(rows, null, 3))
        assertEquals(2, anchorListIndex(rows, null, 1))
        assertNull(anchorListIndex(rows, null, 42))
    }

    @Test
    fun anchorIndexAccountsForDividerShift() {
        val rows = listOf(
            ChatTimelineRow.Message(msg(1)),
            ChatTimelineRow.Message(msg(2)),
            ChatTimelineRow.Message(msg(3)),
        )
        // dividerIndex 2 = divider before row 2 -> rendered divider at 3-2=1,
        // rows at/after shift +1: id3->0, id2->2, id1->3.
        assertEquals(0, anchorListIndex(rows, 2, 3))
        assertEquals(2, anchorListIndex(rows, 2, 2))
        assertEquals(3, anchorListIndex(rows, 2, 1))
    }

    @Test
    fun parkForgetsAnchorAndReportsBottom() {
        val key = "n/#c-test-park"
        ChatScrollPositionStore.putAnchor(key, 7L)
        assertEquals(7L, ChatScrollPositionStore.anchor(key))
        ChatScrollPositionStore.markParkedAtBottom(key)
        assertTrue(ChatScrollPositionStore.isParkedAtBottom(key))
        assertNull(ChatScrollPositionStore.anchor(key))
        ChatScrollPositionStore.unpark(key)
        assertFalse(ChatScrollPositionStore.isParkedAtBottom(key))
        ChatScrollPositionStore.remove(key)
    }
}
