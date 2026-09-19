package pm.antani.resentin.domain.repository

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import pm.antani.resentin.R
import pm.antani.resentin.data.db.AppDatabase
import pm.antani.resentin.data.db.MessageEntity
import pm.antani.resentin.data.db.MessagePagingSource
import pm.antani.resentin.data.prefs.channelKey
import pm.antani.resentin.domain.events.WsEvent
import pm.antani.resentin.domain.session.ConnectionManager
import pm.antani.resentin.net.AppJson
import pm.antani.resentin.net.RateLimitException
import pm.antani.resentin.irc.canonicalTarget
import pm.antani.resentin.net.dto.RateLimitErrorDto
import pm.antani.resentin.net.dto.ReadCursorRequestDto
import pm.antani.resentin.net.dto.ScrollbackMessageDto
import pm.antani.resentin.net.dto.SendMessageDto
import pm.antani.resentin.net.rest.MessagesApi
import pm.antani.resentin.net.rest.UploadsApi

private const val BACKFILL_LIMIT = 200
private const val PAGE_LIMIT = 50
// A single after=lastId&limit=200 fetch only ever proves "at least 200 more" — a busy
// channel can pile up more than that while the app is backgrounded/disconnected, which
// silently truncated the catch-up to the first page forever (nothing re-requested the
// rest). Page forward up to this many full pages before giving up.
private const val BACKFILL_MAX_PAGES = 5

// Local cache retention — the server is the durable copy of scrollback (it always
// answers a backfill for whatever a channel actually needs), so an old, already-read
// row sitting in Room is pure unbounded growth with no upside. 90 days comfortably
// covers "scroll back a while", without the cache ever really acting like an archive.
private const val MESSAGE_RETENTION_DAYS = 90L

class ChatRepository(
    private val authRepository: AuthRepository,
    private val db: AppDatabase,
    private val context: Context,
) {
    /** Subscribes once, app-wide, to WS message events and writes them to Room — the
     * single writer for scrollback, independent of which chat screen (if any) is open. */
    fun startListening(connectionManager: ConnectionManager, scope: CoroutineScope) {
        scope.launch {
            connectionManager.events.collect { event ->
                if (event is WsEvent.MessageReceived) {
                    recordIncoming(event.message)
                }
            }
        }
    }

    fun observeMessages(networkSlug: String, channelName: String): Flow<List<MessageEntity>> =
        db.messageDao().observeMessages(networkSlug, canonicalTarget(channelName))
    fun observeMessageCount(networkSlug: String, channelName: String): Flow<Int> =
        db.messageDao().observeMessageCount(networkSlug, canonicalTarget(channelName))

    /** Room-backed paging for long transcripts. The source opens at the newest
     * locally cached rows; older rows are loaded when the reader reaches the top.
     * Grappa remains the remote history boundary and fills Room through [loadOlder]. */
    fun observeMessagesPaged(networkSlug: String, channelName: String): Flow<PagingData<MessageEntity>> =
        Pager(
            config = PagingConfig(
                pageSize = PAGE_LIMIT,
                initialLoadSize = PAGE_LIMIT * 2,
                prefetchDistance = 12,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                MessagePagingSource(db, networkSlug, canonicalTarget(channelName))
            },
        ).flow

    /** Latest chat message per channel, keyed by lower-cased "network/channel" (same
     * scheme as [pm.antani.resentin.data.prefs.channelKey]) — the Home row preview. */
    fun observeLatestPerChannel(): Flow<Map<String, MessageEntity>> =
        db.messageDao().observeLatestPerChannel()
            .map { rows ->
                rows.associateBy { row ->
                    channelKey(row.networkSlug, canonicalTarget(row.channelName))
                }
            }

    /** Startup maintenance (see AppContainer.init) — drops cached messages older than
     * [MESSAGE_RETENTION_DAYS] across every channel, so the local cache doesn't grow
     * forever on a long-lived install. Best-effort: a failure here is silently swallowed
     * rather than surfaced anywhere, since this is background housekeeping, not a user
     * action with an outcome to report. */
    suspend fun pruneOldMessages() {
        val cutoff = System.currentTimeMillis() - MESSAGE_RETENTION_DAYS * 24 * 60 * 60 * 1000
        runCatching { db.messageDao().deleteOlderThan(cutoff) }
    }

    /** The manual "svuota database messaggi" settings action — every channel's local
     * scrollback, gone at once. The server is untouched; each channel just re-backfills
     * from scratch the next time it's opened.
     *
     * SQLite's DELETE never shrinks anything on its own, so without cleanup here the
     * size shown right after "clearing" would be unchanged and the button would look
     * like it did nothing — confirmed live (deleteAll() alone left the reported size at
     * a flat 518.6 KB). Two separate reclaims are needed, not one: VACUUM compacts
     * `resentin.db` itself (freed pages otherwise just sit on an internal free list for
     * future inserts to reuse), but Room's WAL journal mode means most of that 518.6 KB
     * was actually sitting in the `-wal` side file, which VACUUM does not shrink —
     * `wal_checkpoint(TRUNCATE)` is what flushes it into the main file and truncates it
     * back down. Both must run outside any transaction and off the main thread — Room
     * forbids the latter (see AuthRepository.signIn's clearAllTables, the same footgun).
     * Not run after [pruneOldMessages]'s much smaller, once-per-launch trim: rewriting
     * the whole file on every cold start isn't worth it for that. */
    suspend fun clearAllMessages() {
        withContext(Dispatchers.IO) {
            db.messageDao().deleteAll()
            val writable = db.openHelper.writableDatabase
            writable.execSQL("VACUUM")
            writable.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
        }
    }

    /** Total on-disk size of the message cache — the `-wal` file can hold a meaningful
     * share of not-yet-checkpointed writes on an active install, so it's counted too;
     * a bare `resentin.db` size alone would under-report right after heavy chat activity. */
    fun messageDatabaseSizeBytes(): Long {
        val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
        val walFile = File(dbFile.path + "-wal")
        return dbFile.length() + walFile.length()
    }

    /** For queries, `channel` on a scrollback row is the raw PRIVMSG target — our own
     * nick when the *other* party sent it, the partner's nick when we sent it — so the
     * two directions land under different keys unless normalized to the partner's nick. */
    private suspend fun queryBucket(message: ScrollbackMessageDto): String {
        val messageChannel = canonicalTarget(message.channel)
        val ownNick = db.networkDao().nickForSlug(message.network)?.let(::canonicalTarget)
            ?: return messageChannel
        return if (messageChannel == ownNick) canonicalTarget(message.sender) else messageChannel
    }

    suspend fun recordIncoming(message: ScrollbackMessageDto) {
        recordIncoming(listOf(message))
    }

    private suspend fun recordIncoming(messages: List<ScrollbackMessageDto>) {
        if (messages.isEmpty()) return
        db.messageDao().upsertAll(
            messages.map { message ->
                MessageEntity(
                networkSlug = message.network,
                channelName = queryBucket(message),
                id = message.id,
                serverTime = message.serverTime,
                kind = message.kind,
                sender = message.sender,
                body = message.body,
                metaJson = AppJson.encodeToString(JsonObject.serializer(), message.meta),
                )
            },
        )
    }

    suspend fun sendMessage(networkSlug: String, channelName: String, body: String, ctcpTarget: String? = null): Result<Unit> = runCatching {
        val api = authRepository.api(MessagesApi::class.java)
        val response = api.sendMessage(networkSlug, channelName, SendMessageDto(body, ctcpTarget))
        if (response.code() == 429) {
            val retryAfterMs = runCatching {
                AppJson.decodeFromString(RateLimitErrorDto.serializer(), response.errorBody()?.string().orEmpty())
                    .retryAfterMs
            }.getOrNull()
            throw RateLimitException(retryAfterMs)
        }
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        response.body()?.let { recordIncoming(it) }
    }

    /** Service-targeted variant of [sendMessage] — see
     * [MessagesApi.sendServiceMessage]: the ack body is not a message row and must
     * not be decoded or recorded. */
    suspend fun sendServiceMessage(networkSlug: String, service: String, body: String): Result<Unit> = runCatching {
        val api = authRepository.api(MessagesApi::class.java)
        val response = api.sendServiceMessage(networkSlug, service, SendMessageDto(body))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    /** Fills the gap since the last locally-known message — called after (re)connecting
     * to a channel, since the WS event stream does not replay history, only REST does.
     * Also callable on demand (ChatViewModel.refresh) as the manual "reload" a user has
     * no other way to trigger.
     *
     * Pages forward up to [BACKFILL_MAX_PAGES] full pages so a gap bigger than one
     * 200-row fetch actually drains instead of silently stopping at the first page. A
     * gap wider than that (the app was away long enough, or the channel busy enough,
     * to pile up 1000+ rows) is abandoned in favor of landing at the tail — the rows
     * strictly between the old anchor and the new tail are a real, accepted gap;
     * [loadOlder]'s backward pagination starts from the local minimum, which is on the
     * OLD side of that gap, so it can't reach across it either. */
    suspend fun backfill(networkSlug: String, channelName: String): Result<Unit> = runCatching {
        val api = authRepository.api(MessagesApi::class.java)
        val canonicalChannelName = canonicalTarget(channelName)
        val lastId = db.messageDao().maxId(networkSlug, canonicalChannelName)
        if (lastId == null) {
            recordIncoming(api.getMessages(networkSlug, channelName, limit = BACKFILL_LIMIT))
            return@runCatching
        }

        var anchor = lastId
        repeat(BACKFILL_MAX_PAGES) {
            val page = api.getMessages(networkSlug, channelName, after = anchor, limit = BACKFILL_LIMIT)
            recordIncoming(page)
            if (page.size < BACKFILL_LIMIT) return@runCatching
            anchor = page.maxOf { it.id }
        }
        recordIncoming(api.getMessages(networkSlug, channelName, limit = BACKFILL_LIMIT))
    }

    /** Loads a page of history older than the earliest locally-known message, for
     * scroll-up pagination. */
    suspend fun loadOlder(networkSlug: String, channelName: String): Result<Unit> = runCatching {
        val api = authRepository.api(MessagesApi::class.java)
        val canonicalChannelName = canonicalTarget(channelName)
        val oldestId = db.messageDao().minId(networkSlug, canonicalChannelName) ?: return@runCatching
        val messages = api.getMessages(networkSlug, channelName, before = oldestId, limit = PAGE_LIMIT)
        recordIncoming(messages)
    }

    /** Tells the server we've read up to [messageId] (monotonic advance-only server-side,
     * so a stale/out-of-order call is harmless) and mirrors it into the local cache. */
    suspend fun markRead(networkSlug: String, channelName: String, messageId: Long): Result<Unit> = runCatching {
        val api = authRepository.api(MessagesApi::class.java)
        val response = api.setReadCursor(networkSlug, channelName, ReadCursorRequestDto(messageId))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        db.channelDao().advanceLastReadMessageId(networkSlug, channelName, messageId)
    }

    /** Marks a channel fully read from outside the chat screen (e.g. a long-press on
     * Home) — advances the cursor to the newest locally-known message and zeroes the
     * unread badge immediately rather than waiting on the `window_counts` broadcast,
     * which only reaches this device while the channel's WS topic happens to be
     * joined. No-ops (successfully) on a channel with no cached messages yet. */
    suspend fun markAllRead(networkSlug: String, channelName: String): Result<Unit> = runCatching {
        val lastId = db.messageDao().maxId(networkSlug, canonicalTarget(channelName))
            ?: return@runCatching
        markRead(networkSlug, channelName, lastId).getOrThrow()
        db.channelDao().updateUnreadCounts(networkSlug, channelName, 0, 0, "none")
    }

    /** Uploads [bytes] via the embedded media endpoint, then sends the resulting URL as
     * an ordinary chat message — the server has no separate "attachment" message kind,
     * cicchetto itself just posts the upload URL as the PRIVMSG body (the extension in
     * the URL is what tells a viewer it's an image/video/etc., not a message flag).
     *
     * [expireSeconds]: per-upload TTL on the server's `1h/12h/24h/72h` ladder
     * (see `UPLOAD_TTL_LADDER_SECONDS`) — `null` omits the field and the server
     * default (24h) applies. */
    suspend fun uploadAndSend(
        networkSlug: String,
        channelName: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        expireSeconds: Int? = null,
    ): Result<Unit> = runCatching {
        val uploadsApi = authRepository.api(UploadsApi::class.java)
        val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", fileName, body)
        val expire = expireSeconds?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val response = uploadsApi.upload(part, expire)
        check(response.isSuccessful) { context.getString(R.string.upload_error_http, response.code()) }
        val url = checkNotNull(response.body()) { context.getString(R.string.upload_error_invalid_response) }.url
        sendMessage(networkSlug, channelName, url).getOrThrow()
    }
}
