package pm.antani.resentin.domain.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import pm.antani.resentin.data.db.AppDatabase
import pm.antani.resentin.data.db.ChannelEntity
import pm.antani.resentin.data.db.NetworkEntity
import pm.antani.resentin.data.db.NetworkWithChannels
import pm.antani.resentin.domain.events.WsEvent
import pm.antani.resentin.domain.session.ConnectionManager
import pm.antani.resentin.irc.formatChannelModes
import pm.antani.resentin.net.AppJson
import pm.antani.resentin.net.dto.ArchiveEntryDto
import pm.antani.resentin.net.dto.ChannelDto
import pm.antani.resentin.net.dto.ChannelModesEntryDto
import pm.antani.resentin.net.dto.ConnectionStateUpdateDto
import pm.antani.resentin.net.dto.DirectoryPageDto
import pm.antani.resentin.net.dto.IdentityUpdateDto
import pm.antani.resentin.net.dto.JoinChannelRequestDto
import pm.antani.resentin.net.dto.NetworkDto
import pm.antani.resentin.net.dto.PerformDto
import pm.antani.resentin.net.dto.PerformUpdateDto
import pm.antani.resentin.net.dto.QueryWindowsListDto
import pm.antani.resentin.net.dto.TopicUpdateDto
import pm.antani.resentin.net.rest.NetworkSettingsApi
import pm.antani.resentin.net.rest.NetworksApi

private const val SERVER_PSEUDO_CHANNEL = "\$server"

class NetworksRepository(
    private val authRepository: AuthRepository,
    private val db: AppDatabase,
) {
    val networksWithChannels: Flow<List<NetworkWithChannels>> = db.networkDao().observeNetworksWithChannels()

    fun observeNetwork(slug: String): Flow<NetworkEntity?> = db.networkDao().observeNetwork(slug)

    fun observeChannel(slug: String, channel: String): Flow<ChannelEntity?> =
        db.channelDao().observeChannel(slug, channel)

    /** The channel's current raw simple-mode letters + params (e.g. `+l 50`), decoded
     * from [ChannelEntity.modesRawJson] — the formatted [ChannelEntity.modes] string
     * can't be parsed back for editing. */
    fun observeChannelModes(slug: String, channel: String): Flow<ChannelModesEntryDto> =
        db.channelDao().observeChannel(slug, channel).map { entity ->
            entity?.modesRawJson?.let {
                runCatching { AppJson.decodeFromString(ChannelModesEntryDto.serializer(), it) }.getOrNull()
            } ?: ChannelModesEntryDto()
        }

    suspend fun networkIdForSlug(slug: String): Int? = db.networkDao().idForSlug(slug)

    /** One-shot snapshot, not a live Flow — callers use this to capture "where was the
     * reader before this session touched anything", e.g. the initial chat scroll target. */
    suspend fun getStoredReadCursor(networkSlug: String, channelName: String): Long? =
        db.channelDao().getLastReadMessageId(networkSlug, channelName)

    /** Keeps each channel's stored topic current from `topic_changed` events, which the
     * server pushes unsolicited on channel join — no dedicated REST GET exists for it. */
    fun startListening(connectionManager: ConnectionManager, scope: CoroutineScope) {
        connectionManager.events
            .filterIsInstance<WsEvent.TopicChanged>()
            .map { it.topic }
            .onEach { db.channelDao().updateTopic(it.network, it.channel, it.topic.text) }
            .launchIn(scope)

        // Pushed right after a channel join (if cached) and again live on every MODE line.
        connectionManager.events
            .filterIsInstance<WsEvent.ChannelModesChanged>()
            .map { it.payload }
            .onEach { payload ->
                val display = formatChannelModes(payload.modes.modes, payload.modes.params)
                val rawJson = AppJson.encodeToString(ChannelModesEntryDto.serializer(), payload.modes)
                db.channelDao().updateModes(payload.network, payload.channel, display, rawJson)
            }
            .launchIn(scope)

        // query_windows_list has no REST equivalent (GET .../channels never returns
        // queries) — it's the only source of truth for which DMs are currently open,
        // stored as ChannelEntity rows tagged source="query" so they ride the same
        // Home list / chat navigation as real channels.
        connectionManager.events
            .filterIsInstance<WsEvent.QueryWindowsListReceived>()
            .onEach { applyQueryWindows(it.windows) }
            .launchIn(scope)

        // Cross-device sync: another client (e.g. cicchetto on the web) advancing the
        // read cursor broadcasts read_cursor_set on the per-channel topic.
        connectionManager.events
            .filterIsInstance<WsEvent.ReadCursorSet>()
            .onEach { event ->
                val (slug, channel) = parseChannelTopic(event.topic) ?: return@onEach
                db.channelDao().advanceLastReadMessageId(slug, channel, event.lastReadMessageId)
            }
            .launchIn(scope)

        // Live unread-badge updates, pushed on every new message in a joined channel.
        connectionManager.events
            .filterIsInstance<WsEvent.WindowCountsChanged>()
            .onEach { event ->
                val (slug, _) = parseChannelTopic(event.topic) ?: return@onEach
                db.channelDao().updateUnreadCounts(slug, event.channel, event.messages, event.mentions, event.severity)
            }
            .launchIn(scope)
    }

    /** Records a read-cursor value we already know is current (e.g. from a channel
     * join's `read_cursor` field) without waiting for a `read_cursor_set` broadcast. */
    suspend fun recordReadCursor(networkSlug: String, channelName: String, messageId: Long?) {
        if (messageId != null) db.channelDao().advanceLastReadMessageId(networkSlug, channelName, messageId)
    }

    /** Seeds both the read-cursor and the unread-badge counts from a channel join's
     * response — the door both `read_cursor_set` and `window_counts` broadcasts refresh
     * live afterwards. Called for every channel topic joined app-wide (see
     * AppContainer), so Home's badges are populated as soon as the socket connects, not
     * only once a chat screen happens to be opened. No-ops for a non-channel topic
     * (user/network joins, where [parseChannelTopic] fails) or an already-joined topic
     * ([response] is null). */
    suspend fun applyJoinResponse(topic: String, response: JsonObject?) {
        val (networkSlug, channelName) = parseChannelTopic(topic) ?: return
        if (response == null) return
        val cursor = response["read_cursor"]?.let { el -> if (el is JsonNull) null else el.jsonPrimitive.longOrNull }
        recordReadCursor(networkSlug, channelName, cursor)

        val counts = response["window_counts"] as? JsonObject ?: return
        val messages = counts["messages"]?.jsonPrimitive?.intOrNull ?: 0
        val mentions = counts["mentions"]?.jsonPrimitive?.intOrNull ?: 0
        val severity = counts["severity"]?.jsonPrimitive?.contentOrNull ?: "none"
        db.channelDao().updateUnreadCounts(networkSlug, channelName, messages, mentions, severity)
    }

    private fun parseChannelTopic(topic: String): Pair<String, String>? {
        val parts = topic.split("/")
        if (parts.size < 3) return null
        val slug = parts[1].removePrefix("network:")
        val channel = parts[2].removePrefix("channel:")
        return slug to channel
    }

    private suspend fun applyQueryWindows(dto: QueryWindowsListDto) {
        for ((networkIdRaw, windows) in dto.windows) {
            val networkId = networkIdRaw.toIntOrNull() ?: continue
            val slug = db.networkDao().slugForId(networkId) ?: continue
            db.channelDao().upsertAll(
                windows.map { window ->
                    ChannelEntity(networkSlug = slug, name = window.targetNick, source = "query", joined = true)
                },
            )
            db.channelDao().deleteMissingQueries(slug, windows.map { it.targetNick })
        }
    }

    suspend fun refresh(): Result<Unit> = runCatching {
        val api = authRepository.api(NetworksApi::class.java)
        val networks = api.getNetworks()

        db.networkDao().upsertAll(networks.map { it.toEntity() })
        db.networkDao().deleteMissing(networks.map { it.slug })

        for (network in networks) {
            val channels = api.getChannels(network.slug)
            db.channelDao().upsertAll(channels.map { it.toEntity(network.slug) })
            db.channelDao().deleteMissing(network.slug, channels.map { it.name })

            // "$server" is a fixed per-network pseudo-channel carrying MOTD/service
            // notices (NickServ, ChanServ, ...) — always present, not listed by
            // GET .../channels, joined/fetched via the same generic (network, channel)
            // pipeline as a real channel.
            db.channelDao().upsertAll(
                listOf(ChannelEntity(networkSlug = network.slug, name = SERVER_PSEUDO_CHANNEL, source = "server", joined = true)),
            )
        }
    }

    suspend fun updateIdentity(slug: String, nick: String?, ident: String?, realname: String?): Result<Unit> =
        runCatching {
            val api = authRepository.api(NetworkSettingsApi::class.java)
            val response = api.updateIdentity(slug, IdentityUpdateDto(nick, ident, realname))
            check(response.isSuccessful) { "HTTP ${response.code()}" }
            refresh().getOrThrow()
        }

    suspend fun updateConnectionState(slug: String, connected: Boolean): Result<Unit> = runCatching {
        val api = authRepository.api(NetworkSettingsApi::class.java)
        val state = if (connected) "connected" else "parked"
        val response = api.updateConnectionState(slug, ConnectionStateUpdateDto(state))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        refresh().getOrThrow()
    }

    suspend fun getPerform(slug: String): Result<PerformDto> = runCatching {
        authRepository.api(NetworkSettingsApi::class.java).getPerform(slug)
    }

    suspend fun updatePerform(slug: String, performList: String?): Result<PerformDto> = runCatching {
        authRepository.api(NetworkSettingsApi::class.java).updatePerform(slug, PerformUpdateDto(performList))
    }

    suspend fun updateTopic(slug: String, channel: String, text: String): Result<Unit> = runCatching {
        val api = authRepository.api(NetworkSettingsApi::class.java)
        val response = api.updateTopic(slug, channel, TopicUpdateDto(text))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun partChannel(slug: String, channel: String): Result<Unit> = runCatching {
        val api = authRepository.api(NetworkSettingsApi::class.java)
        val response = api.partChannel(slug, channel)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        refresh().getOrThrow()
    }

    /** JOINs [name] (a channel, optionally +k-keyed) on [slug]. The row appears via the
     * follow-up [refresh] as `:pending`/`:joined` per the server's own JOIN lifecycle —
     * this does not itself wait for the upstream JOIN to land. */
    suspend fun joinChannel(slug: String, name: String, key: String? = null): Result<Unit> = runCatching {
        val api = authRepository.api(NetworksApi::class.java)
        val response = api.joinChannel(slug, JoinChannelRequestDto(name, key))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        refresh().getOrThrow()
    }

    suspend fun getDirectory(slug: String, sort: String, q: String? = null, cursor: String? = null): Result<DirectoryPageDto> =
        runCatching {
            authRepository.api(NetworksApi::class.java).getDirectory(slug, sort, q, cursor)
        }

    suspend fun refreshDirectory(slug: String): Result<Unit> = runCatching {
        val api = authRepository.api(NetworksApi::class.java)
        val response = api.refreshDirectory(slug)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun getArchive(slug: String): Result<List<ArchiveEntryDto>> = runCatching {
        authRepository.api(NetworksApi::class.java).getArchive(slug).archive
    }

    suspend fun deleteArchiveEntry(slug: String, target: String): Result<Unit> = runCatching {
        val api = authRepository.api(NetworksApi::class.java)
        val response = api.deleteArchiveEntry(slug, target)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }
}

private fun NetworkDto.toEntity() = NetworkEntity(
    slug = slug,
    id = id,
    nick = nick,
    ident = ident,
    realname = realname,
    connectionState = connectionState,
    connectionStateReason = connectionStateReason,
    connectionStateChangedAt = connectionStateChangedAt,
    server = connection?.server,
    port = connection?.port,
    tls = connection?.tls,
)

private fun ChannelDto.toEntity(networkSlug: String) = ChannelEntity(
    networkSlug = networkSlug,
    name = name,
    source = source,
    joined = joined,
)
