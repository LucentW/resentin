package pm.antani.resentin.domain.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
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
import pm.antani.resentin.domain.session.channelTopic
import pm.antani.resentin.domain.session.ConnectionManager
import pm.antani.resentin.irc.canonicalTarget
import pm.antani.resentin.irc.formatChannelModes
import pm.antani.resentin.net.AppJson
import pm.antani.resentin.net.dto.ArchiveEntryDto
import pm.antani.resentin.net.dto.ChannelDto
import pm.antani.resentin.net.dto.ChannelModesEntryDto
import pm.antani.resentin.net.dto.ConnectionStateUpdateDto
import pm.antani.resentin.net.dto.DirectoryPageDto
import pm.antani.resentin.net.dto.FeaturedChannelDto
import pm.antani.resentin.net.dto.IdentityUpdateDto
import pm.antani.resentin.net.dto.JoinChannelRequestDto
import pm.antani.resentin.net.dto.NetworkDto
import pm.antani.resentin.net.dto.SessionNetworkRequestDto
import pm.antani.resentin.net.dto.NotifyRequestDto
import pm.antani.resentin.net.dto.PerformDto
import pm.antani.resentin.net.dto.PerformUpdateDto
import pm.antani.resentin.net.dto.ProfileUpdateDto
import pm.antani.resentin.net.dto.QueryWindowsListDto
import pm.antani.resentin.net.dto.TopicUpdateDto
import pm.antani.resentin.net.rest.NetworkSettingsApi
import pm.antani.resentin.net.rest.NetworksApi
import pm.antani.resentin.net.rest.NotifyApi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

private const val SERVER_PSEUDO_CHANNEL = "\$server"

/** An inbound IRC INVITE not yet joined or declined — see `window_invited` in
 * [pm.antani.resentin.domain.events.WsEvent]. */
data class PendingInvite(val networkSlug: String, val channel: String, val inviter: String)

/** A peer's `DCC SEND` held awaiting Accept/Decline (issue 2089 on grappa-irc) — see
 * `dcc_offer` in [pm.antani.resentin.domain.events.WsEvent]. [offerId] is the unique
 * key (unlike [PendingInvite], several offers can target the same channel at once). */
data class PendingDccOffer(
    val networkSlug: String,
    val channel: String,
    val offerId: String,
    val from: String,
    val filename: String,
    val size: Long,
)

class NetworksRepository(
    private val authRepository: AuthRepository,
    private val db: AppDatabase,
    private val connectionManager: ConnectionManager,
) {
    private val unreadSyncMutex = Mutex()

    val networksWithChannels: Flow<List<NetworkWithChannels>> = db.networkDao().observeNetworksWithChannels()

    /** Per-network services-identity verdicts (cicchetto #388 parity): the set of
     * network ids this session is identified to NickServ on. Seeded by the
     * `session_identity_changed` user-topic event — live edge + cold snapshot both
     * flow through it, last-write-wins. Unseeded (absent) reads as NOT identified,
     * so the register-nick affordance shows rather than hides pre-snapshot. */
    private val _identifiedNetworkIds = MutableStateFlow<Set<Int>>(emptySet())
    val identifiedNetworkIds: StateFlow<Set<Int>> = _identifiedNetworkIds.asStateFlow()

    /** Invites awaiting a Join/Decline, seeded by `window_invited`'s cold-subscribe
     * snapshot (pushed on the per-user topic, joined unconditionally at connect —
     * no REST endpoint lists these) and kept live by the same event plus
     * `window_invite_declined`. Session-local server-side, so there's nothing to
     * persist to the DB across reconnects: a fresh connect always re-snapshots it. */
    private val _pendingInvites = MutableStateFlow<List<PendingInvite>>(emptyList())
    val pendingInvites: StateFlow<List<PendingInvite>> = _pendingInvites.asStateFlow()

    /** Fires only for a GENUINELY new invite (not yet in [pendingInvites]) — the
     * cold-subscribe snapshot re-sends `window_invited` for every still-pending invite
     * on every reconnect (foreground resume, `stayConnected` cycling, ...), and a
     * system notification firing on every one of those would spam the user for an
     * invite they simply haven't acted on yet. [NotificationRouter] posts from this. */
    private val _newInvites = MutableSharedFlow<PendingInvite>(extraBufferCapacity = 8)
    val newInvites: SharedFlow<PendingInvite> = _newInvites.asSharedFlow()

    /** Held DCC offers awaiting Accept/Decline — same cold-subscribe-snapshot-plus-live
     * pattern as [pendingInvites] (`dcc_offer`/`dcc_offer_resolved` on the per-user topic). */
    private val _pendingDccOffers = MutableStateFlow<List<PendingDccOffer>>(emptyList())
    val pendingDccOffers: StateFlow<List<PendingDccOffer>> = _pendingDccOffers.asStateFlow()

    /** Fires only for a genuinely new offer — same reconnect-dedup reasoning as [newInvites]. */
    private val _newDccOffers = MutableSharedFlow<PendingDccOffer>(extraBufferCapacity = 8)
    val newDccOffers: SharedFlow<PendingDccOffer> = _newDccOffers.asSharedFlow()

    /** Last query_windows_list snapshot. Re-applied at the end of every REST
     * refresh: the snapshot can win the race against refresh() repopulating the
     * networks table (fresh install / post-migration wipe → slugForId misses and
     * the rows are dropped), and REST has no query endpoint to converge with
     * otherwise — without this the DMs stay missing until the next reconnect. */
    @Volatile
    private var lastQueryWindows: QueryWindowsListDto? = null

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

        // M3b — a DM partner's avatar, seeded synchronously on a WHOIS bundle and/or
        // patched live once a lazy fetch (opt-in `show_peer_profiles`, JOIN/353-triggered
        // — not necessarily an explicit /whois) lands. Both a no-op UPDATE when the nick
        // isn't a known query row: there is no INSERT here, so a stranger never spawns a
        // Home row just because their avatar happened to resolve.
        connectionManager.events
            .filterIsInstance<WsEvent.WhoisBundle>()
            .map { it.whois }
            .onEach { whois ->
                if (whois.network.isBlank() || whois.avatarUrl == null) return@onEach
                db.channelDao().updateAvatarUrl(whois.network, whois.target, whois.avatarUrl)
            }
            .launchIn(scope)

        connectionManager.events
            .filterIsInstance<WsEvent.AvatarReady>()
            .map { it.avatar }
            .onEach { avatar ->
                if (avatar.network.isBlank() || avatar.avatarUrl == null) return@onEach
                db.channelDao().updateAvatarUrl(avatar.network, avatar.nick, avatar.avatarUrl)
            }
            .launchIn(scope)

        // query_windows_list has no REST equivalent (GET .../channels never returns
        // queries) — it's the only source of truth for which DMs are currently open,
        // stored as ChannelEntity rows tagged source="query" so they ride the same
        // Home list / chat navigation as real channels.
        connectionManager.events
            .filterIsInstance<WsEvent.QueryWindowsListReceived>()
            .onEach {
                lastQueryWindows = it.windows
                applyQueryWindows(it.windows)
            }
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

        // Normalized services-identity verdict (see identifiedNetworkIds) — the
        // registration wizard's launcher gate + step-6 auto-complete signal.
        connectionManager.events
            .filterIsInstance<WsEvent.SessionIdentityChanged>()
            .onEach { event ->
                _identifiedNetworkIds.update { current ->
                    if (event.identified) current + event.networkId else current - event.networkId
                }
            }
            .launchIn(scope)

        // Live unread-badge updates, pushed on every new message in a joined channel.
        connectionManager.events
            .filterIsInstance<WsEvent.WindowCountsChanged>()
            .onEach { event ->
                val (slug, _) = parseChannelTopic(event.topic) ?: return@onEach
                db.channelDao().updateUnreadCounts(slug, event.channel, event.messages, event.mentions, event.severity)

                // For an inbound DM the server's live window_counts event is keyed
                // by our own nick, while the Home query row is keyed by the peer.
                // Re-read the server's complete envelope in that case so the count is
                // applied to the canonical private-chat row as well. The mutex keeps
                // a burst of join/message events from producing overlapping /me calls.
                if (event.channel.equals(db.networkDao().nickForSlug(slug), ignoreCase = true)) {
                    syncUnreadCountsFromMe()
                }
            }
            .launchIn(scope)

        connectionManager.events
            .filterIsInstance<WsEvent.WindowInvited>()
            .map { it.invite }
            .onEach { invite ->
                val isNew = _pendingInvites.value.none { it.networkSlug == invite.network && it.channel == invite.channel }
                _pendingInvites.update { current ->
                    current.filterNot { it.networkSlug == invite.network && it.channel == invite.channel } +
                        PendingInvite(invite.network, invite.channel, invite.inviter)
                }
                if (isNew) _newInvites.tryEmit(PendingInvite(invite.network, invite.channel, invite.inviter))
            }
            .launchIn(scope)

        connectionManager.events
            .filterIsInstance<WsEvent.WindowInviteDeclined>()
            .map { it.declined }
            .onEach { declined ->
                _pendingInvites.update { current ->
                    current.filterNot { it.networkSlug == declined.network && it.channel == declined.channel }
                }
            }
            .launchIn(scope)

        connectionManager.events
            .filterIsInstance<WsEvent.DccOffer>()
            .map { it.offer }
            .onEach { offer ->
                val isNew = _pendingDccOffers.value.none { it.offerId == offer.offerId }
                val pending = PendingDccOffer(offer.network, offer.channel, offer.offerId, offer.from, offer.filename, offer.size)
                _pendingDccOffers.update { current -> current.filterNot { it.offerId == offer.offerId } + pending }
                if (isNew) _newDccOffers.tryEmit(pending)
            }
            .launchIn(scope)

        connectionManager.events
            .filterIsInstance<WsEvent.DccOfferResolved>()
            .map { it.resolved }
            .onEach { resolved ->
                _pendingDccOffers.update { current -> current.filterNot { it.offerId == resolved.offerId } }
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
            syncMembership(slug, windows.map { window ->
                ChannelEntity(networkSlug = slug, name = window.targetNick, source = "query", joined = true)
            })
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
            syncMembership(network.slug, channels.map { it.toEntity(network.slug) })
            db.channelDao().deleteMissing(network.slug, channels.map { it.name })

            // "$server" is a fixed per-network pseudo-channel carrying MOTD/service
            // notices (NickServ, ChanServ, ...) — always present, not listed by
            // GET .../channels, joined/fetched via the same generic (network, channel)
            // pipeline as a real channel.
            syncMembership(
                network.slug,
                listOf(ChannelEntity(networkSlug = network.slug, name = SERVER_PSEUDO_CHANNEL, source = "server", joined = true)),
            )
        }

        // /me carries the server-authoritative seed for windows that already have a
        // read cursor. REST endpoints for networks/channels intentionally do not.
        // Re-apply the last query snapshot too (see lastQueryWindows): refresh()
        // may have just re-created the networks the snapshot previously missed.
        lastQueryWindows?.let { applyQueryWindows(it) }
        syncUnreadCountsFromMe()
    }

    /** Membership-only sync: inserts unknown channels, refreshes source/joined on the
     * known ones, and never touches the live WS-fed fields (topic, modes, read cursor,
     * unread badges) — the REST payloads simply don't carry them. */
    private suspend fun syncMembership(networkSlug: String, channels: List<ChannelEntity>) {
        db.channelDao().insertMissing(channels)
        channels.forEach { db.channelDao().updateMembership(networkSlug, it.name, it.source, it.joined) }

        // A channel joined through some OTHER route (e.g. Directory) while its invite
        // banner was still up leaves no server-side :invited state to re-snapshot on
        // the next reconnect — nothing would otherwise clear the stale banner.
        val joinedNow = channels.filter { it.joined }.map { canonicalTarget(it.name) }.toSet()
        if (joinedNow.isNotEmpty()) {
            _pendingInvites.update { current ->
                current.filterNot { it.networkSlug == networkSlug && canonicalTarget(it.channel) in joinedNow }
            }
        }
    }

    /** Applies the server-authoritative /me unread envelope to rows already known
     * locally. Unknown query rows are intentionally left alone: query_windows_list
     * may still be in flight, and its join reply will seed the row once it exists. */
    private suspend fun syncUnreadCountsFromMe() {
        unreadSyncMutex.lock()
        try {
            authRepository.getMe().getOrNull()?.unreadCounts?.forEach { (slug, channels) ->
                channels.forEach { (channel, counts) ->
                    db.channelDao().updateUnreadCounts(
                        networkSlug = slug,
                        name = channel,
                        messages = counts.messages,
                        mentions = counts.mentions,
                        severity = counts.severity,
                    )
                }
            }
        } finally {
            unreadSyncMutex.unlock()
        }
    }

    suspend fun updateIdentity(slug: String, nick: String?, ident: String?, realname: String?): Result<Unit> =
        runCatching {
            val api = authRepository.api(NetworkSettingsApi::class.java)
            val response = api.updateIdentity(slug, IdentityUpdateDto(nick, ident, realname))
            check(response.isSuccessful) { "HTTP ${response.code()}" }
            refresh().getOrThrow()
        }

    suspend fun updateProfile(
        slug: String,
        age: String,
        gender: String,
        location: String,
        languages: String,
        custom: String,
    ): Result<Unit> = runCatching {
        val api = authRepository.api(NetworkSettingsApi::class.java)
        val response = api.updateProfile(slug, ProfileUpdateDto(age, gender, location, languages, custom))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        refresh().getOrThrow()
    }

    suspend fun uploadAvatar(slug: String, bytes: ByteArray, mimeType: String): Result<Unit> = runCatching {
        val api = authRepository.api(NetworkSettingsApi::class.java)
        val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", "avatar", body)
        val response = api.uploadAvatar(slug, part)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        refresh().getOrThrow()
    }

    suspend fun deleteAvatar(slug: String): Result<Unit> = runCatching {
        val api = authRepository.api(NetworkSettingsApi::class.java)
        val response = api.deleteAvatar(slug)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        refresh().getOrThrow()
    }

    /** Fetches an avatar's raw bytes (the own or a peer's) — thin pass-through onto
     * [AuthRepository.fetchBytes] so screens outside [UserCardController] don't need
     * their own auth-scoped HTTP client just to decode a preview bitmap. */
    suspend fun fetchAvatarBytes(url: String): ByteArray? = authRepository.fetchBytes(url)

    suspend fun updateConnectionState(slug: String, connected: Boolean, reason: String? = null): Result<Unit> = runCatching {
        val api = authRepository.api(NetworkSettingsApi::class.java)
        val state = if (connected) "connected" else "parked"
        val response = api.updateConnectionState(slug, ConnectionStateUpdateDto(state, reason))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        refresh().getOrThrow()
    }

    /** The `/notify` presence watch list for this network (GH #247) — nicks only, no
     * online/offline state (that rides a separate live `presence` field this client
     * doesn't track yet). Fetched on demand, like [IgnoresRepository]'s list: no
     * broadcast to mirror, so a settings screen just refreshes on open. */
    suspend fun getNotifyList(slug: String): Result<List<String>> = runCatching {
        authRepository.api(NotifyApi::class.java).list(slug).entries.map { it.nick }
    }

    suspend fun addNotify(slug: String, nicks: List<String>): Result<Unit> = runCatching {
        val response = authRepository.api(NotifyApi::class.java).add(slug, NotifyRequestDto(nicks))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
    }

    suspend fun removeNotify(slug: String, nick: String): Result<Unit> = runCatching {
        val response = authRepository.api(NotifyApi::class.java).remove(slug, nick)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
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

    /** Leaves a channel's Phoenix topic on an actual PART, mirroring cicchetto's own
     * "close a channel tab = real PART = phx_leave" behavior (subscribe.ts) — this app
     * otherwise never leaves a topic once joined, so without this a parted channel would
     * stay subscribed (still receiving whatever the server pushes to it) until the next
     * full reconnect. Best-effort: the subject may be unknown (signed out mid-call) or
     * the topic may never have been joined this session, either of which is harmless to
     * skip — there's nothing live to tear down. */
    suspend fun partChannel(slug: String, channel: String, reason: String? = null): Result<Unit> = runCatching {        val api = authRepository.api(NetworkSettingsApi::class.java)
        val response = api.partChannel(slug, channel, reason)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        authRepository.session.value?.wsSubject?.let { subject ->
            connectionManager.leaveChannel(channelTopic(subject, slug, channel))
        }
        refresh().getOrThrow()
    }

    /** Drops a DM window locally right after `close_query_window` succeeds. The server
     * does not reliably re-broadcast `query_windows_list` on close, so waiting for it
     * leaves dead query rows in Home (reported as "the DM never disappears"). Same
     * Phoenix-topic teardown as [partChannel], best-effort. */
    suspend fun closeLocalQuery(slug: String, nick: String) {
        db.channelDao().deleteQuery(slug, nick)
        authRepository.session.value?.wsSubject?.let { subject ->
            connectionManager.leaveChannel(channelTopic(subject, slug, nick))
        }
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

    /** Accepts a pending invite: a plain JOIN (the server has no separate "accept"
     * verb — see `InvitesController`'s moduledoc). Drops the local banner optimistically
     * since the server only clears its own `:invited` state as a side effect of the
     * JOIN, with no dedicated event to react to. */
    suspend fun acceptInvite(slug: String, channel: String): Result<Unit> =
        joinChannel(slug, channel).onSuccess {
            _pendingInvites.update { current -> current.filterNot { it.networkSlug == slug && it.channel == channel } }
        }

    suspend fun declineInvite(slug: String, channel: String): Result<Unit> = runCatching {
        val api = authRepository.api(NetworksApi::class.java)
        val response = api.declineInvite(slug, channel)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        _pendingInvites.update { current -> current.filterNot { it.networkSlug == slug && it.channel == channel } }
    }

    /** Consents to a held DCC offer. 202 means admission only — the delivered file
     * arrives later as a normal scrollback row (`Grappa.Dcc.Report`), not from this
     * call, so this just drops the offer's own banner optimistically. */
    suspend fun acceptDccOffer(slug: String, offerId: String): Result<Unit> = runCatching {
        val api = authRepository.api(NetworksApi::class.java)
        val response = api.acceptDccOffer(slug, offerId)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        _pendingDccOffers.update { current -> current.filterNot { it.offerId == offerId } }
    }

    suspend fun declineDccOffer(slug: String, offerId: String): Result<Unit> = runCatching {
        val api = authRepository.api(NetworksApi::class.java)
        val response = api.declineDccOffer(slug, offerId)
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        _pendingDccOffers.update { current -> current.filterNot { it.offerId == offerId } }
    }

    suspend fun getDirectory(slug: String, sort: String, q: String? = null, cursor: String? = null): Result<DirectoryPageDto> =
        runCatching {
            authRepository.api(NetworksApi::class.java).getDirectory(slug, sort, q, cursor)
        }

    suspend fun getFeaturedChannels(slug: String): Result<List<FeaturedChannelDto>> =
        runCatching {
            authRepository.api(NetworksApi::class.java).getFeaturedChannels(slug).channels
        }

    /** One-tap attach for a network offered in `/me.home_data.available_networks`. */
    suspend fun addSessionNetwork(slug: String): Result<Unit> = runCatching {
        val response = authRepository.api(NetworksApi::class.java)
            .addSessionNetwork(SessionNetworkRequestDto(slug))
        check(response.isSuccessful) { "HTTP ${response.code()}" }
        refresh().getOrThrow()
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
    profileAge = age,
    profileGender = gender,
    profileLocation = location,
    profileLanguages = languages,
    profileCustom = custom,
    avatarUrl = avatarUrl,
    servicesFlavor = servicesFlavor,
)

private fun ChannelDto.toEntity(networkSlug: String) = ChannelEntity(
    networkSlug = networkSlug,
    name = name,
    source = source,
    joined = joined,
)
