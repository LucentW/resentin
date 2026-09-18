package pm.antani.resentin.ui.chat

/**
 * Keeps the logical "follow the newest message" intent separate from the physical
 * LazyColumn position. A list can move while paging, inserting rows, or the IME
 * changes the viewport; none of those movements should be mistaken for a reader
 * scrolling away from the tail.
 */
internal class ChatAutoFollowTracker {
    var following: Boolean = true
        private set

    private var newestMessageId: Long? = null

    fun reset(atBottom: Boolean, newestMessageId: Long?) {
        following = atBottom
        this.newestMessageId = newestMessageId
    }

    fun requestFollow() {
        following = true
    }

    fun stopFollowing() {
        following = false
    }

    fun onScrollStateChanged(
        scrolling: Boolean,
        programmatic: Boolean,
        atBottom: Boolean,
    ) {
        if (programmatic) return
        following = if (scrolling) false else atBottom
    }

    fun onTimelineChanged(newestMessageId: Long?): Boolean {
        val previousNewestMessageId = this.newestMessageId
        val isNewerMessage = newestMessageId != null &&
            (previousNewestMessageId == null || newestMessageId > previousNewestMessageId)

        if (newestMessageId != null &&
            (previousNewestMessageId == null || newestMessageId > previousNewestMessageId)
        ) {
            this.newestMessageId = newestMessageId
        }

        return following && isNewerMessage
    }
}