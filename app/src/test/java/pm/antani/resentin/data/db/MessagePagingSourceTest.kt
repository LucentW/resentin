package pm.antani.resentin.data.db

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagePagingSourceTest {
    @Test
    fun readerAtTheLiveEndRefreshesFromTheNewestRows() {
        assertTrue(MessagePagingSource.isNearTail(0))
        assertTrue(MessagePagingSource.isNearTail(MessagePagingSource.TAIL_ANCHOR_WINDOW - 1))
    }

    @Test
    fun readerFarBackKeepsTheAnchoredRefresh() {
        assertFalse(MessagePagingSource.isNearTail(MessagePagingSource.TAIL_ANCHOR_WINDOW))
    }
}
