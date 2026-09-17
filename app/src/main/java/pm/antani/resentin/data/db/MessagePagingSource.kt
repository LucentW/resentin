package pm.antani.resentin.data.db

import androidx.paging.PagingSource
import androidx.paging.PagingState

/**
 * A Room-backed source that opens at the newest local rows while keeping the
 * natural chronological order required by the chat. Paging's prepend side then
 * asks for older rows as the reader moves toward the top of the transcript.
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

        val requestedOffset = params.key
        val offset = requestedOffset ?: (total - params.loadSize).coerceAtLeast(0)
        val rows = dao.loadMessagesPage(networkSlug, channelName, params.loadSize, offset)
        val actualEnd = offset + rows.size
        LoadResult.Page(
            data = rows,
            prevKey = if (offset > 0) (offset - params.loadSize).coerceAtLeast(0) else null,
            nextKey = if (actualEnd < total) actualEnd else null,
        )
    }.getOrElse { LoadResult.Error(it) }

    override fun getRefreshKey(state: PagingState<Int, MessageEntity>): Int? {
        val anchor = state.anchorPosition ?: return null
        val item = state.closestItemToPosition(anchor) ?: return null
        val page = state.pages.firstOrNull { page -> page.data.any { it.id == item.id } } ?: return null
        val indexInPage = page.data.indexOfFirst { it.id == item.id }
        return (page.prevKey ?: 0) + indexInPage
    }
}
