package pm.antani.resentin.domain.repository

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import pm.antani.resentin.R
import pm.antani.resentin.data.db.AppDatabase
import pm.antani.resentin.data.db.MessageEntity
import pm.antani.resentin.domain.events.WsEvent
import pm.antani.resentin.domain.session.ConnectionManager
import pm.antani.resentin.net.AppJson
import pm.antani.resentin.net.RateLimitException
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
        db.messageDao().observeMessages(networkSlug, channelName)

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
        val ownNick = db.networkDao().nickForSlug(message.network) ?: return message.channel
        return if (message.channel.equals(ownNick, ignoreCase = true)) message.sender else message.channel
    }

    suspend fun recordIncoming(message: ScrollbackMessageDto) {
        db.messageDao().upsert(
            MessageEntity(
                networkSlug = message.network,
                channelName = queryBucket(message),
                id = message.id,
                serverTime = message.serverTime,
                kind = message.kind,
                sender = message.sender,
                body = message.body,
                metaJson = AppJson.encodeToString(JsonObject.serializer(), message.meta),
            ),
        )
    }

    suspend fun sendMessage(networkSlug: String, channelName: String, body: String): Result<Unit> = runCatching {
        val api = authRepository.api(MessagesApi::class.java)
        val response = api.sendMessage(networkSlug, channelName, SendMessageDto(body))
        if (response.code() == 429) {
            val retryAfterMs = runCatching {
                AppJson.decodeFromString(RateLimitErrorDto.serializer(), response.errorBody()?.string().orEmpty())
                    .retryAfterMs
            }.getOrNull()
            throw RateLimitException(retryAfterMs)
        }
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
        val lastId = db.messageDao().maxId(networkSlug, channelName)
        if (lastId == null) {
            api.getMessages(networkSlug, channelName, limit = BACKFILL_LIMIT).forEach { recordIncoming(it) }
            return@runCatching
        }

        var anchor = lastId
        repeat(BACKFILL_MAX_PAGES) {
            val page = api.getMessages(networkSlug, channelName, after = anchor, limit = BACKFILL_LIMIT)
            page.forEach { recordIncoming(it) }
            if (page.size < BACKFILL_LIMIT) return@runCatching
            anchor = page.maxOf { it.id }
        }
        api.getMessages(networkSlug, channelName, limit = BACKFILL_LIMIT).forEach { recordIncoming(it) }
    }

    /** Loads a page of history older than the earliest locally-known message, for
     * scroll-up pagination. */
    suspend fun loadOlder(networkSlug: String, channelName: String): Result<Unit> = runCatching {
        val api = authRepository.api(MessagesApi::class.java)
        val oldestId = db.messageDao().minId(networkSlug, channelName) ?: return@runCatching
        val messages = api.getMessages(networkSlug, channelName, before = oldestId, limit = PAGE_LIMIT)
        messages.forEach { recordIncoming(it) }
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
        val lastId = db.messageDao().maxId(networkSlug, channelName) ?: return@runCatching
        markRead(networkSlug, channelName, lastId).getOrThrow()
        db.channelDao().updateUnreadCounts(networkSlug, channelName, 0, 0, "none")
    }

    /** Uploads [bytes] via the embedded media endpoint, then sends the resulting URL as
     * an ordinary chat message — the server has no separate "attachment" message kind,
     * cicchetto itself just posts the upload URL as the PRIVMSG body (the extension in
     * the URL is what tells a viewer it's an image/video/etc., not a message flag). */
    suspend fun uploadAndSend(
        networkSlug: String,
        channelName: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Result<Unit> = runCatching {
        val uploadsApi = authRepository.api(UploadsApi::class.java)
        val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", fileName, body)
        val response = uploadsApi.upload(part)
        check(response.isSuccessful) { context.getString(R.string.upload_error_http, response.code()) }
        val url = checkNotNull(response.body()) { context.getString(R.string.upload_error_invalid_response) }.url
        sendMessage(networkSlug, channelName, url).getOrThrow()
    }
}
