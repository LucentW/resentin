package pm.antani.resentin.data.db

import androidx.paging.PagingSource
import androidx.paging.PagingState

/**
 * A Room-backed source ordered for a reverse-layout chat: index 0 is the newest
 * message and Paging append loads progressively older rows.
 */
class MessagePagingSource(
    private val db: AppDatabase,
    private val networkSlug: String,
    private val channelName: String,
) : PagingSource<Int, MessageEntity>() {

    private val observer = object : androidx.room.InvalidationTracker.Observer("messages") {
        override fun onInvalidated(tables: Set<String>) {
            invalidate()
        }
    }

    init {
        db.invalidationTracker.addObserver(observer)
        registerInvalidatedCallback {
            db.invalidationTracker.removeObserver(observer)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MessageEntity> = runCatching {
        val dao = db.messageDao()
        val total = dao.countMessages(networkSlug, channelName)
        if (total == 0) {
            return LoadResult.Page(emptyList(), prevKey = null, nextKey = null)
        }

        val descendingOffset = params.key ?: 0
        val ascendingOffset = (total - descendingOffset - params.loadSize).coerceAtLeast(0)
        val rows = dao.loadMessagesPage(networkSlug, channelName, params.loadSize, ascendingOffset)
            .asReversed()
        val actualDescendingEnd = descendingOffset + rows.size
        LoadResult.Page(
            data = rows,
            prevKey = if (descendingOffset > 0) {
                (descendingOffset - params.loadSize).coerceAtLeast(0)
            } else {
                null
            },
            nextKey = if (ascendingOffset > 0) actualDescendingEnd else null,
        )
    }.getOrElse { LoadResult.Error(it) }

    override fun getRefreshKey(state: PagingState<Int, MessageEntity>): Int? {
        val anchor = state.anchorPosition ?: return null
        val item = state.closestItemToPosition(anchor) ?: return null
        val pageIndex = state.pages.indexOfFirst { page -> page.data.any { it.id == item.id } }
        if (pageIndex < 0) return null
        val indexInPage = state.pages[pageIndex].data.indexOfFirst { it.id == item.id }
        return state.pages.take(pageIndex).sumOf { it.data.size } + indexInPage
    }
}
