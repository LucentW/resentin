package pm.antani.resentin.ui.chat

/**
 * #360 — cicchetto port: mention-aware jump-to-bottom badge geometry.
 *
 * Mirror of cicchetto's `mentionScroll.ts`. Resentin renders one row per
 * message entity and — like cicchetto's unread-divider bump — inserts a single
 * divider row in front of the first unread message, so message `i` occupies
 * list index `i (+1 if the divider precedes it)`. `mentionRowIndices` are the
 * ASCENDING list indices of the mention rows (every row a hilight/watch pattern
 * marks from a sender other than the operator's own nick — the SAME rows the
 * per-row highlight paints, `isMentionRow`).
 *
 * "Below the fold" is cicchetto's rule verbatim: a mention counts once its row
 * lies ENTIRELY past the last laid-out row; one straddling the fold has its top
 * above the viewport end and is therefore already seen. The row-index gate
 * `index > lastVisibleRowIndex` agrees with it — a mention at the fold has
 * index == lastVisibleRowIndex and is dropped either way, and any mention
 * beyond the last laid-out row starts strictly below it.
 *
 * The boolean this produces is a pure function of list indices — no DOM
 * geometry read — so it ports cleanly onto Resentin's LazyColumn layout info
 * and stays unit-testable, exactly like the cicchetto module it mirrors.
 */
object MentionScroll {

    /**
     * The mention rows that still sit below the fold, NEAREST FIRST (ascending
     * list index — the intended scroll direction is always downward, so the
     * nearest below-fold mention is the first one past the last laid-out row).
     * Keeps chronological order for a deterministic "which one does the tap
     * jump to". Empty when every mention has been seen or none exist.
     */
    fun mentionRowsBelowFold(mentionRowIndices: List<Int>, lastVisibleRowIndex: Int): List<Int> =
        mentionRowIndices.dropWhile { it <= lastVisibleRowIndex }

    /**
     * Reverse-layout equivalent: lower list indices are newer and sit toward
     * the chat tail. These mentions remain on the tail side of the fold.
     */
    fun mentionRowsBeforeFold(mentionRowIndices: List<Int>, firstVisibleRowIndex: Int): List<Int> =
        mentionRowIndices.takeWhile { it < firstVisibleRowIndex }.asReversed()

    fun reverseBadgeCount(mentionRowIndices: List<Int>, firstVisibleRowIndex: Int): Int =
        mentionRowsBeforeFold(mentionRowIndices, firstVisibleRowIndex).size
    /** Badge count = how many mentions are still below the fold. */
    fun badgeCount(mentionRowIndices: List<Int>, lastVisibleRowIndex: Int): Int =
        mentionRowsBelowFold(mentionRowIndices, lastVisibleRowIndex).size

    /**
     * The NEAREST mention row below the fold the tap should jump to, or null
     * when none — in which case the button falls back to plain scroll-to-bottom.
     */
    fun nextMentionRowIndex(mentionRowIndices: List<Int>, lastVisibleRowIndex: Int): Int? =
        mentionRowsBelowFold(mentionRowIndices, lastVisibleRowIndex).firstOrNull()
}
