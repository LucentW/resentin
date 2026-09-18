package pm.antani.resentin.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class MentionScrollTest {

    @Test
    fun `counts only mentions below the last visible row`() {
        val rows = listOf(2, 7, 11, 18)

        assertEquals(2, MentionScroll.badgeCount(rows, lastVisibleRowIndex = 7))
    }

    @Test
    fun `a mention at the fold is already considered seen`() {
        val rows = listOf(4, 9)

        assertEquals(emptyList<Int>(), MentionScroll.mentionRowsBelowFold(rows, lastVisibleRowIndex = 9))
        assertEquals(null, MentionScroll.nextMentionRowIndex(rows, lastVisibleRowIndex = 9))
    }

    @Test
    fun `next mention is the nearest one below in chronological order`() {
        val rows = listOf(3, 12, 20)

        assertEquals(12, MentionScroll.nextMentionRowIndex(rows, lastVisibleRowIndex = 5))
    }

    @Test
    fun `reverse layout counts newer mentions while reading history`() {
        val rows = listOf(1, 4, 8, 13)

        assertEquals(listOf(8, 4, 1), MentionScroll.mentionRowsBeforeFold(rows, firstVisibleRowIndex = 9))
        assertEquals(3, MentionScroll.reverseBadgeCount(rows, firstVisibleRowIndex = 9))
    }

    @Test
    fun `reverse layout has no badge at the chat tail`() {
        assertEquals(emptyList<Int>(), MentionScroll.mentionRowsBeforeFold(listOf(1, 3), firstVisibleRowIndex = 0))
    }
    @Test
    fun `no mentions gives no badge or target`() {
        assertEquals(0, MentionScroll.badgeCount(emptyList(), lastVisibleRowIndex = 10))
        assertEquals(null, MentionScroll.nextMentionRowIndex(emptyList(), lastVisibleRowIndex = 10))
    }
}
