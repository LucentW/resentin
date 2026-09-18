package pm.antani.resentin.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAutoFollowTest {
    @Test
    fun followsNewerTimelineEntryWhenAlreadyAtBottom() {
        val tracker = ChatAutoFollowTracker()
        tracker.reset(atBottom = true, newestMessageId = 10L)

        assertTrue(tracker.onTimelineChanged(11L))
    }

    @Test
    fun doesNotFollowOlderHistoryOrAnUnchangedTimeline() {
        val tracker = ChatAutoFollowTracker()
        tracker.reset(atBottom = true, newestMessageId = 10L)

        assertFalse(tracker.onTimelineChanged(9L))
        assertFalse(tracker.onTimelineChanged(10L))
    }

    @Test
    fun userScrollUpDisablesFollowingUntilSettlingAtBottom() {
        val tracker = ChatAutoFollowTracker()
        tracker.reset(atBottom = true, newestMessageId = 10L)

        tracker.onScrollStateChanged(scrolling = true, programmatic = false, atBottom = false)
        assertFalse(tracker.onTimelineChanged(11L))

        tracker.onScrollStateChanged(scrolling = false, programmatic = false, atBottom = true)
        assertTrue(tracker.onTimelineChanged(12L))
    }

    @Test
    fun programmaticScrollDoesNotDisableFollowing() {
        val tracker = ChatAutoFollowTracker()
        tracker.reset(atBottom = true, newestMessageId = 10L)

        tracker.onScrollStateChanged(scrolling = true, programmatic = true, atBottom = false)

        assertTrue(tracker.onTimelineChanged(11L))
    }

    @Test
    fun explicitNewestRequestRestoresFollowing() {
        val tracker = ChatAutoFollowTracker()
        tracker.reset(atBottom = false, newestMessageId = 10L)

        tracker.requestFollow()

        assertTrue(tracker.onTimelineChanged(11L))
    }

    @Test
    fun resetInitialSnapshotIsNotTreatedAsLiveArrival() {
        val tracker = ChatAutoFollowTracker()
        tracker.reset(atBottom = true, newestMessageId = 42L)

        assertFalse(tracker.onTimelineChanged(42L))
    }
    @Test
    fun emptyInitialSnapshotFollowsItsFirstMessage() {
        val tracker = ChatAutoFollowTracker()
        tracker.reset(atBottom = true, newestMessageId = null)

        assertTrue(tracker.onTimelineChanged(42L))
    }

    @Test
    fun missingOrLowerIdsNeverTriggerLiveFollow() {
        val tracker = ChatAutoFollowTracker()
        tracker.reset(atBottom = true, newestMessageId = 42L)

        assertFalse(tracker.onTimelineChanged(null))
        assertFalse(tracker.onTimelineChanged(41L))
    }
}