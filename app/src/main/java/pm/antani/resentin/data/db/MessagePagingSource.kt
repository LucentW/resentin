package pm.antani.resentin.data.db

import androidx.paging.PagingSource
import androidx.paging.PagingState

/**
 * A Room-backed source ordered for a reverse-layout chat: index 0 is the newest
 * message and Paging append loads progressively older rows.
 *
 * Keys are message ids (keyset windows), never OFFSETs: re-reading a COUNT on
 * every load shifts all offsets when a new row arrives and pages skip or repeat
 * rows. A [LoadResult.Page.nextKey] is the exclusive older bound (`id < key`),
 * [LoadResult.Page.prevKey] the exclusive newer bound (`id > key`); a refresh
 * anchors inclusively on the visible row so it stays in the reloaded page.
 *
 * No table-wide invalidation here: Room only observes whole tables, so any
 * other chat's traffic would rebuild this list mid-read. The screen refreshes
 * this source off its own channel-scoped row count instead (see ChatScreen).
 */
class MessagePagingSource(
    private val db: AppDatabase,
    private val networkSlug: String,
    private val channelName: String,
) : PagingSource<Long, MessageEntity>() {

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, MessageEntity> = runCatching {
        val dao = db.messageDao()
        val rows = when (params) {
            is LoadParams.Refresh -> {
                val key = params.key
                if (key == null) {
                    dao.loadNewestPage(networkSlug, channelName, params.loadSize)
                } else {
                    dao.loadPageAtOrBefore(networkSlug, channelName, key, params.loadSize)
                }
            }
            is LoadParams.Prepend ->
                dao.loadNewerThan(networkSlug, channelName, params.key, params.loadSize).asReversed()
            is LoadParams.Append ->
                dao.loadOlderThan(networkSlug, channelName, params.key, params.loadSize)
        }
        if (rows.isEmpty()) {
            // An empty window terminates its own direction; the head needs no
            // extra probe query to prove there is nothing newer.
            return LoadResult.Page(emptyList(), prevKey = null, nextKey = null)
        }
        val fullPage = rows.size >= params.loadSize
        LoadResult.Page(
            data = rows,
            prevKey = rows.first().id,
            nextKey = if (fullPage) rows.last().id else null,
        )
    }.getOrElse { LoadResult.Error(it) }

    override fun getRefreshKey(state: PagingState<Long, MessageEntity>): Long? {
        val anchor = state.anchorPosition ?: return null
        // Inclusive anchor: the refresh window (`id <= key`) keeps the visible
        // row itself instead of starting just below it.
        return state.closestItemToPosition(anchor)?.id
    }
}
