package pm.antani.resentin.domain.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
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

class ChatRepository(
    private val authRepository: AuthRepository,
    private val db: AppDatabase,
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
     * to a channel, since the WS event stream does not replay history, only REST does. */
    suspend fun backfill(networkSlug: String, channelName: String): Result<Unit> = runCatching {
        val api = authRepository.api(MessagesApi::class.java)
        val lastId = db.messageDao().maxId(networkSlug, channelName)
        val messages = if (lastId == null) {
            api.getMessages(networkSlug, channelName, limit = BACKFILL_LIMIT)
        } else {
            api.getMessages(networkSlug, channelName, after = lastId, limit = BACKFILL_LIMIT)
        }
        messages.forEach { recordIncoming(it) }
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
        check(response.isSuccessful) { "Upload fallito: HTTP ${response.code()}" }
        val url = checkNotNull(response.body()) { "Risposta di upload non valida" }.url
        sendMessage(networkSlug, channelName, url).getOrThrow()
    }
}
