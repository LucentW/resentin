package pm.antani.resentin.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pm.antani.resentin.R
import pm.antani.resentin.data.db.MessageEntity
import pm.antani.resentin.data.prefs.AppPreferences
import pm.antani.resentin.data.prefs.ChatDisplayMode
import pm.antani.resentin.data.prefs.MessageDensity
import pm.antani.resentin.domain.repository.AuthRepository
import pm.antani.resentin.domain.repository.ChatRepository
import pm.antani.resentin.domain.repository.IgnoresRepository
import pm.antani.resentin.domain.repository.MembersRepository
import pm.antani.resentin.domain.repository.NetworksRepository
import pm.antani.resentin.domain.repository.PendingDccOffer
import pm.antani.resentin.domain.repository.UserSettingsRepository
import pm.antani.resentin.domain.session.channelTopic
import pm.antani.resentin.domain.session.ConnectionManager
import pm.antani.resentin.domain.session.OpenChatTracker
import pm.antani.resentin.domain.session.PendingShareHolder
import pm.antani.resentin.irc.canonicalTarget
import pm.antani.resentin.irc.isMessageVisibleUnderPresenceFilter
import pm.antani.resentin.irc.isQueryTarget
import pm.antani.resentin.irc.isServiceNick
import pm.antani.resentin.irc.MessageLines
import pm.antani.resentin.irc.presenceVisible
import pm.antani.resentin.irc.serviceNickFor
import pm.antani.resentin.net.RateLimitException
import pm.antani.resentin.net.dto.LinksBundleDto
import pm.antani.resentin.net.dto.LusersBundleDto
import pm.antani.resentin.net.dto.ServerReplyDto
import pm.antani.resentin.net.dto.UPLOAD_TTL_LADDER_SECONDS
import pm.antani.resentin.net.dto.WhoReplyDto
import pm.antani.resentin.net.dto.WhowasBundleDto
import pm.antani.resentin.net.dto.effectiveUploadTtlSeconds
import pm.antani.resentin.ui.common.UserCardController
import pm.antani.resentin.ui.common.stripMircCodes

sealed interface ChatCommandEffect {
    data class OpenChannel(val channelName: String) : ChatCommandEffect
    data class ConfirmChannelJoin(val channelName: String) : ChatCommandEffect
    data object CloseChat : ChatCommandEffect
    data object OpenChannelSettings : ChatCommandEffect
    data object OpenAppSettings : ChatCommandEffect
}

/** #1883 — a picked/shared file staged behind the pre-upload confirm dialog.
 * Non-null while the dialog is up; the bytes are read only on confirm, so a
 * declined pick never pays the read. */
data class PendingUploadConfirm(
    val uri: Uri,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String,
    /** TTL choice in seconds, preselected with the effective default. */
    val ttlSeconds: Int,
)
/** A reply selected in the composer, kept separate from the text being written. */
data class PendingReply(
    val nick: String,
    val messageBody: String,
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val networksRepository: NetworksRepository,
    private val membersRepository: MembersRepository,
    private val ignoresRepository: IgnoresRepository,
    private val authRepository: AuthRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val appPreferences: AppPreferences,
    private val connectionManager: ConnectionManager,
    private val openChatTracker: OpenChatTracker,
    private val pendingShareHolder: PendingShareHolder,
    private val appContext: Context,
    private val networkSlug: String,
    private val channelName: String,
    private val username: String,
    private val subject: String,
) : ViewModel() {

    val topic: StateFlow<String?> = networksRepository.observeChannel(networkSlug, channelName)
        .map { it?.topic?.takeIf { topic -> topic.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val channelModes: StateFlow<String?> = networksRepository.observeChannel(networkSlug, channelName)
        .map { it?.modes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** M3b — a query's DM partner avatar, for the top bar. `null` on a real channel: a
     * channel row's members aren't individually avatar-tracked (see `ChannelEntity`). */
    val peerAvatarUrl: StateFlow<String?> = networksRepository.observeChannel(networkSlug, channelName)
        .map { it?.avatarUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun fetchAvatarBytes(url: String): ByteArray? = networksRepository.fetchAvatarBytes(url)

    // The viewer's own current nick on this network — same source NotificationRouter
    // reads to decide whether an incoming message deserves a notification, reused here
    // (via irc.containsMention) so a message highlighted as "mentions you" in chat can
    // never drift from one that actually fired a notification.
    val myNick: StateFlow<String?> = networksRepository.observeNetwork(networkSlug)
        .map { it?.nick }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Explicit away state for this network ("away" or null) — fed by the
     * `away_confirmed` push, which also fires for writes from another device. */
    val awayState: StateFlow<String?> = membersRepository.awayByNetwork
        .map { it[networkSlug] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Standalone 301 away notice for the peer in this query window. */
    val peerAwayMessage: StateFlow<String?> = membersRepository.peerAwayByNetwork
        .map { it[networkSlug]?.get(canonicalTarget(channelName)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismissPeerAway() = membersRepository.dismissPeerAway(networkSlug, channelName)

    /** `/hilight` watchlist patterns (null = never loaded — match nick-only
     * until the first `list` reply lands). Warmed once the user topic is joined. */
    val highlightPatterns: StateFlow<List<String>?> = userSettingsRepository.highlightPatterns

    /** DCC offers (issue 2089 on grappa-irc) held for THIS window — a stranger's offer
     * routes to `$server`, so this is never empty there for long on a busy bouncer.
     * Passthrough-filtered from [NetworksRepository.pendingDccOffers], which is shared
     * app-wide since the same offer's banner must show wherever it was pushed. */
    val pendingDccOffers: StateFlow<List<PendingDccOffer>> = networksRepository.pendingDccOffers
        .map { offers -> offers.filter { it.networkSlug == networkSlug && canonicalTarget(it.channel) == canonicalTarget(channelName) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun acceptDccOffer(offer: PendingDccOffer) {
        viewModelScope.launch {
            networksRepository.acceptDccOffer(networkSlug, offer.offerId)
                .onFailure { _error.value = it.message ?: appContext.getString(R.string.home_unknown_error) }
        }
    }

    fun declineDccOffer(offer: PendingDccOffer) {
        viewModelScope.launch {
            networksRepository.declineDccOffer(networkSlug, offer.offerId)
                .onFailure { _error.value = it.message ?: appContext.getString(R.string.home_unknown_error) }
        }
    }

    // Ephemeral `/whowas` result card — last-write-wins per network, like cicchetto's
    // one-bundle-per-slug card. Dismissed by the user, replaced by the next reply.
    private val _whowas = MutableStateFlow<WhowasBundleDto?>(null)
    val whowas: StateFlow<WhowasBundleDto?> = _whowas.asStateFlow()
    fun dismissWhowas() { _whowas.value = null }

    // Ephemeral `/who` roster modal — same last-write-wins discipline.
    private val _whoReply = MutableStateFlow<WhoReplyDto?>(null)
    val whoReply: StateFlow<WhoReplyDto?> = _whoReply.asStateFlow()
    fun dismissWho() { _whoReply.value = null }

    // Ephemeral `/lusers` card. The server also auto-emits a bundle on connect
    // welcome, so [lusersRequested] gates consumption: only the reply to an actual
    // `/lusers` is shown, then the gate closes (cicchetto's markLusersRequested).
    private val _lusers = MutableStateFlow<LusersBundleDto?>(null)
    val lusers: StateFlow<LusersBundleDto?> = _lusers.asStateFlow()
    private var lusersRequested = false
    fun dismissLusers() { _lusers.value = null }

    // Ephemeral `/links` topology snapshot — same last-write-wins discipline
    // as who (an empty bundle is still a snapshot: restricted vs no-match).
    private val _links = MutableStateFlow<LinksBundleDto?>(null)
    val links: StateFlow<LinksBundleDto?> = _links.asStateFlow()
    fun dismissLinks() { _links.value = null }

    // Ephemeral server-text reply (`info`/`version`/`motd`/`admin`) — one
    // modal surface for all four sources, last-write-wins per network.
    private val _serverReply = MutableStateFlow<ServerReplyDto?>(null)
    val serverReply: StateFlow<ServerReplyDto?> = _serverReply.asStateFlow()
    fun dismissServerReply() { _serverReply.value = null }

    // Server-driven recover progress (app-wide in MembersRepository; shown
    // here only when it belongs to this chat's network).
    val recover = membersRepository.recoverState
    fun dismissRecover() = membersRepository.dismissRecover()

    // Umode viewer/editor target (network slug, null = closed) — cicchetto
    // umodeModal parity. Data (active + supported sets) stays server-owned in
    // MembersRepository; this is only which network the modal is open for.
    private val _umodeModal = MutableStateFlow<String?>(null)
    val umodeModal: StateFlow<String?> = _umodeModal.asStateFlow()
    fun openUmodeModal(networkSlug: String) { _umodeModal.value = networkSlug }
    fun dismissUmodeModal() { _umodeModal.value = null }

    val umodeNetworkId: StateFlow<Int?> = combine(
        networksRepository.networksWithChannels,
        _umodeModal,
    ) { networks, slug ->
        slug?.let { s -> networks.firstOrNull { it.network.slug.equals(s, ignoreCase = true) }?.network?.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleUmode(mode: AvailableUmode, active: Boolean) {
        if (!mode.settable) return
        val networkId = umodeNetworkId.value ?: return
        viewModelScope.launch {
            runCatching {
                membersRepository.setUmode(subject, networkId, (if (active) "-" else "+") + mode.letter)
            }.onFailure { postError(it.message) }
        }
    }

    val umodesByNetworkId = membersRepository.umodesByNetworkId
    val supportedUmodesByNetworkId = membersRepository.supportedUmodesByNetworkId

    // `/hilight` add/del confirmation ("highlight (N): ..."), dismissible.
    private val _highlightNotice = MutableStateFlow<String?>(null)
    val highlightNotice: StateFlow<String?> = _highlightNotice.asStateFlow()
    fun dismissHighlightNotice() { _highlightNotice.value = null }

    private val _showCredits = MutableStateFlow(false)
    val showCredits: StateFlow<Boolean> = _showCredits.asStateFlow()
    fun dismissCredits() { _showCredits.value = false }

    val chatDisplayMode: StateFlow<ChatDisplayMode> = appPreferences.chatDisplayMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatDisplayMode.BUBBLES)

    val messageDensity: StateFlow<MessageDensity> = appPreferences.messageDensity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MessageDensity.NORMAL)

    val showSeconds: StateFlow<Boolean> = appPreferences.showSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val coloredNicklist: StateFlow<Boolean> = appPreferences.coloredNicklist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val showHostmaskInEvents: StateFlow<Boolean> = appPreferences.showHostmaskInEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val smartPresenceFilter: StateFlow<Boolean> = appPreferences.smartPresenceFilter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    // Long-press-on-message user card: same whois/moderation logic as the member list,
    // scoped to this same (network, channel) — empty members/sigils for a query/$server,
    // which naturally hides the channel-only actions in the card.
    private val userCard =
        UserCardController(membersRepository, networksRepository, ignoresRepository, authRepository, networkSlug, channelName, username, subject, viewModelScope)
    val selectedWhois = userCard.selectedWhois
    val avatarBitmap = userCard.avatarBitmap
    val ownSigils = userCard.ownSigils
    val privilegeModes = userCard.privilegeModes
    val navigateToQuery = userCard.navigateToQuery
    // Exposed for the nick role-prefix (~&@%+) shown in both chat display modes.
    val members = userCard.members

    val availableChannels: StateFlow<List<String>> = networksRepository.networksWithChannels
        .map { networks -> networks.firstOrNull { it.network.slug.equals(networkSlug, ignoreCase = true) }?.channels.orEmpty().map { it.name }.filter { it.firstOrNull() in setOf('#', '&', '+', '!') }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availableNetworks: StateFlow<List<String>> = networksRepository.networksWithChannels
        .map { networks -> networks.map { it.network.slug }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val channelPresenceVisible: StateFlow<Boolean> = combine(
        userSettingsRepository.presencePinFlowFor(networkSlug, channelName),
        members,
    ) { pin, currentMembers ->
        if (isQueryTarget(channelName)) true else presenceVisible(pin, currentMembers.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    // Keep the stored rows available for the Activity view even when the server's
    // presence preference hides them from the main transcript.
    val allMessages: StateFlow<List<MessageEntity>> = chatRepository.observeMessages(networkSlug, channelName)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // The server filters historical pages for a hidden presence pin. Apply the same
    // rule locally to live WS rows, which the server still delivers for membership
    // and window-state bookkeeping. Raw rows remain in Room.
    val messages: StateFlow<List<MessageEntity>> = combine(
        allMessages,
        channelPresenceVisible,
        myNick,
    ) { rows, visible, ownNick ->
        rows.filter { row -> isMessageVisibleUnderPresenceFilter(row, visible, ownNick) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onMessageLongPress(nick: String) = userCard.onNickClick(nick)
    fun requestWhois(nick: String) = userCard.onNickClick(nick)
    fun dismissWhois() = userCard.dismissWhois()
    fun kickFromCard(nick: String) = userCard.kick(nick)
    fun banFromCard(nick: String) = userCard.ban(nick)
    fun contactPrivately(nick: String) = userCard.contactPrivately(nick)
    fun setModeFromCard(nick: String, letter: Char, grant: Boolean) = userCard.setMode(nick, letter, grant)
    fun sigilsFor(nick: String) = userCard.sigilsFor(nick)
    fun isIgnored(nick: String): Flow<Boolean> = userCard.isIgnored(nick)
    fun ignore(nick: String) = userCard.ignore(nick)
    fun unignore(nick: String) = userCard.unignore(nick)

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()
    private var draftChangedByUser = false
    private val draftWriteMutex = Mutex()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Multi-line draft awaiting the flood-guard confirmation. Non-null while the
    // "send as N messages?" dialog is up; the draft is NOT cleared underneath it.
    private val _pendingMultiLineSend = MutableStateFlow<String?>(null)
    val pendingMultiLineSend: StateFlow<String?> = _pendingMultiLineSend.asStateFlow()

    private val _isLoadingOlder = MutableStateFlow(false)
    val isLoadingOlder: StateFlow<Boolean> = _isLoadingOlder.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Snapshot of "where the reader left off" BEFORE this session marks anything new as
    // read — the one-time scroll target. Null until known; ChatScreen should wait for a
    // non-null-or-otherwise-settled value before deciding where to land initially.
    private val _initialReadCursor = MutableStateFlow<Long?>(null)
    val initialReadCursor: StateFlow<Long?> = _initialReadCursor.asStateFlow()

    // The initial cursor is intentionally frozen for the first landing position.
    // This live copy advances after the chat is actually marked read so the UI can
    // remove the unread divider without recreating the screen.
    private val _readCursor = MutableStateFlow<Long?>(null)
    val readCursor: StateFlow<Long?> = _readCursor.asStateFlow()

    // Distinguishes "not known yet" from "known to be null" (never read) — the UI must
    // wait for this before deciding where to scroll, or it'll always land on the bottom.
    private val _initialReadCursorReady = MutableStateFlow(false)
    val initialReadCursorReady: StateFlow<Boolean> = _initialReadCursorReady.asStateFlow()

    // Separate from the cursor: the cursor is local and can be known immediately,
    // while the REST history may still be filling the cache in the background.
    private val _initialHistoryReady = MutableStateFlow(false)
    val initialHistoryReady: StateFlow<Boolean> = _initialHistoryReady.asStateFlow()

    private var lastMarkedRead = 0L

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _pendingUpload = MutableStateFlow<PendingUploadConfirm?>(null)
    val pendingUpload: StateFlow<PendingUploadConfirm?> = _pendingUpload.asStateFlow()

    // #1883/#2095 — server upload prefs, loaded on open. Failures keep the
    // server defaults (confirm off, no TTL pref) so an upload never blocks on
    // a prefs fetch — same fail-open posture as the highlight patterns below.
    private var uploadConfirmEnabled = false
    private var uploadTtlPreference: Int? = null

    // One-shot signal for ChatScreen to grab keyboard focus (and move the cursor to the
    // end of the now-prefilled draft) right after a swipe-to-reply — reply() alone only
    // changes the draft's TEXT, which doesn't imply focus or cursor position on its own.
    private val _replyFocusRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val replyFocusRequests: SharedFlow<Unit> = _replyFocusRequests.asSharedFlow()

    private val _pendingReply = MutableStateFlow<PendingReply?>(null)
    val pendingReply: StateFlow<PendingReply?> = _pendingReply.asStateFlow()

    private val _commandEffects = MutableSharedFlow<ChatCommandEffect>(extraBufferCapacity = 1)
    val commandEffects: SharedFlow<ChatCommandEffect> = _commandEffects.asSharedFlow()

    // Tell the visible chat to follow the tail after a message has been accepted.
    // Incoming-message auto-follow stays put while the reader browses older history.
    private val _scrollToLatestAfterSend = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToLatestAfterSend: SharedFlow<Unit> = _scrollToLatestAfterSend.asSharedFlow()
    private val channelReady = CompletableDeferred<Unit>()

    init {
        openChatTracker.onChatOpened(networkSlug, channelName)
        // Arrived here via the Android share sheet (ShareTargetScreen staged the URIs
        // before navigating in) — upload each one now, exactly like tapping the attach
        // button once per file. Consumed once so re-entering this chat later doesn't
        // re-upload the same share.
        pendingShareHolder.consume().forEach { uploadFile(it) }
        viewModelScope.launch {
            userCard.error.collect { message -> message?.let { postError(it) } }
        }
        viewModelScope.launch {
            val savedDraft = runCatching {
                appPreferences.getChatDraft(networkSlug, channelName)
            }.getOrDefault("")
            if (!draftChangedByUser) _draft.value = savedDraft
        }
        viewModelScope.launch {
            val cursor = networksRepository.getStoredReadCursor(networkSlug, channelName)
            _initialReadCursor.value = cursor
            _readCursor.value = cursor
            // The first layout can now use cached messages without waiting for the
            // network join or the full backfill to finish.
            _initialReadCursorReady.value = true
            runCatching {
                connectionManager.connect()
                connectionManager.joinChannel("grappa:user:$subject")
                connectionManager.joinChannel("grappa:user:$subject/network:$networkSlug")
                val channelTopic = channelTopic(subject, networkSlug, channelName)
                networksRepository.applyJoinResponse(channelTopic, connectionManager.joinChannel(channelTopic))
                channelReady.complete(Unit)
                // A join reply only happens once per process lifetime per topic (usually
                // AppContainer's app-wide join already consumed it before this screen
                // even opened, via the same applyJoinResponse path) — if we still have no
                // scroll target by then, fall back to whatever Room ended up with.
                if (_initialReadCursor.value == null) {
                    val cursor = networksRepository.getStoredReadCursor(networkSlug, channelName)
                    _initialReadCursor.value = cursor
                    _readCursor.value = cursor
                }
            }.onFailure {
                if (!channelReady.isCompleted) channelReady.completeExceptionally(it)
                postError(it.message)
            }
            chatRepository.backfill(networkSlug, channelName).onFailure { postError(it.message) }
            _initialHistoryReady.value = true
        }
        // Marks the newest loaded message as read whenever it changes — the chat being
        // open (this ViewModel existing) is already the "the user is looking at this"
        // signal the rest of the app (OpenChatTracker) relies on.
        viewModelScope.launch {
            messages.collect { list -> list.maxByOrNull { it.id }?.let { markRead(it.id) } }
        }
        // Server-query replies for this network only — bundles carry no window
        // context, so anything for another network belongs to a different chat.
        viewModelScope.launch {
            membersRepository.whowasEvents.collect { dto ->
                if (dto.network.equals(networkSlug, ignoreCase = true)) _whowas.value = dto
            }
        }
        viewModelScope.launch {
            membersRepository.whoEvents.collect { dto ->
                if (dto.network.equals(networkSlug, ignoreCase = true)) _whoReply.value = dto
            }
        }
        viewModelScope.launch {
            membersRepository.lusersEvents.collect { dto ->
                if (lusersRequested && dto.network.equals(networkSlug, ignoreCase = true)) {
                    lusersRequested = false
                    _lusers.value = dto
                }
            }
        }
        viewModelScope.launch {
            membersRepository.linksEvents.collect { dto ->
                if (dto.network.equals(networkSlug, ignoreCase = true)) _links.value = dto
            }
        }
        viewModelScope.launch {
            membersRepository.serverReplyEvents.collect { dto ->
                if (dto.network.equals(networkSlug, ignoreCase = true)) _serverReply.value = dto
            }
        }
        // Warm the highlight patterns once the user topic is up — matching stays
        // nick-only until this lands, and silently so on failure.
        viewModelScope.launch {
            runCatching { channelReady.await() }
            runCatching { userSettingsRepository.refreshWatchlist(subject) }
        }
        // #1883/#2095 — the upload confirm + TTL preference. Loads beside the
        // watchlist above; until it lands uploads go out unconfirmed with the
        // server TTL default (a sub-second window right after opening a chat).
        viewModelScope.launch {
            uploadConfirmEnabled = userSettingsRepository.getUploadConfirmEnabled().getOrDefault(false)
            uploadTtlPreference = userSettingsRepository.getUploadTtlSeconds().getOrNull()
        }
    }

    private fun markRead(messageId: Long) {
        if (messageId <= lastMarkedRead) return
        lastMarkedRead = messageId
        viewModelScope.launch {
            chatRepository.markRead(networkSlug, channelName, messageId)
                .onSuccess {
                    _readCursor.value = maxOf(_readCursor.value ?: Long.MIN_VALUE, messageId)
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        openChatTracker.onChatClosed(networkSlug, channelName)
    }

    fun onDraftChange(text: String) {
        setDraft(text)
    }

    /** Open joined channels directly; ask before joining a channel mentioned in chat. */
    fun requestOpenChannelFromReference(target: String) {
        if (!isChannelName(target)) return
        viewModelScope.launch {
            val isJoined = networksRepository.networksWithChannels.first()
                .firstOrNull { it.network.slug.equals(networkSlug, ignoreCase = true) }
                ?.channels
                ?.any { it.joined && canonicalTarget(it.name) == canonicalTarget(target) } == true
            _commandEffects.emit(
                if (isJoined) ChatCommandEffect.OpenChannel(target)
                else ChatCommandEffect.ConfirmChannelJoin(target),
            )
        }
    }

    /** The user confirmed the prompt shown for an unjoined channel reference. */
    fun confirmChannelJoinFromReference(target: String) {
        if (!isChannelName(target)) return
        viewModelScope.launch {
            runCatching {
                channelReady.await()
                networksRepository.joinChannel(networkSlug, target).getOrThrow()
            }.onSuccess {
                _commandEffects.emit(ChatCommandEffect.OpenChannel(target))
            }.onFailure { postError(it.message) }
        }
    }

    private fun setDraft(text: String) {
        draftChangedByUser = true
        _draft.value = text
        viewModelScope.launch {
            draftWriteMutex.withLock {
                appPreferences.setChatDraft(networkSlug, channelName, text)
            }
        }
    }

    /** Selects a message for the visual reply bar. The configured reply template is
     * expanded only at send time, so the user's draft stays plain and editable. */
    fun reply(nick: String, messageBody: String) {
        if (messageBody.isBlank()) return
        _pendingReply.value = PendingReply(nick = nick, messageBody = messageBody)
        _replyFocusRequests.tryEmit(Unit)
    }

    fun cancelReply() {
        _pendingReply.value = null
    }
    /** Message-menu `!addquote`: appends the archive command for [messageBody] to
     * the draft and stops — nothing is sent, whatever quote bot sits in the
     * channel interprets it (cicchetto #1107 parity). The payload carries the
     * sender (`<nick> body`, `* nick body` for actions): what gets quoted is what
     * the operator read. A draft already holding a command earns the bare payload
     * instead of a second verb (one command per line for the bot). */
    fun appendAddQuote(nick: String, messageBody: String, isAction: Boolean) {
        val plain = stripMircCodes(messageBody).trim()
        if (plain.isEmpty()) return
        val payload = if (isAction) "* $nick $plain" else "<$nick> $plain"
        viewModelScope.launch {
            val draft = _draft.value
            val addition = if (!draft.contains(ADDQUOTE_COMMAND)) "$ADDQUOTE_COMMAND$payload"
            else if (draft.endsWith(" ")) payload else " $payload"
            setDraft(draft + addition)
            _replyFocusRequests.tryEmit(Unit)
        }
    }

    fun send() {
        viewModelScope.launch {
            val rawText = _draft.value
            val text = composeOutgoingText(rawText)
            if (text.isBlank()) return@launch
            if (!rawText.trimStart().startsWith("/")) {
                val lines = MessageLines.splitMessageLines(text)
                if (lines.size > MULTI_LINE_CONFIRM_MESSAGES) {
                    _pendingMultiLineSend.value = text
                    return@launch
                }
                if (lines.size > 1) {
                    sendMultiline(text, rawText)
                    return@launch
                }
                sendMessage(text, rawText)
                return@launch
            }
            _pendingReply.value = null
            if (_isSending.value) return@launch
            _isSending.value = true
            try {
                val aliases = userSettingsRepository.getAliases().getOrDefault(emptyMap())
                val expanded = expandUserSlashAlias(rawText, aliases) ?: rawText
                when (val parsed = parseSlashCommand(expanded)) {
                    SlashCommandParseResult.NotACommand -> sendMessage(text, rawText)
                    SlashCommandParseResult.Incomplete -> showSlashError(R.string.chat_slash_command_incomplete)
                    is SlashCommandParseResult.Invalid -> showSlashError(
                        when (parsed.error) {
                            SlashCommandError.UnknownCommand -> R.string.chat_slash_command_unknown
                            SlashCommandError.MissingArgument -> R.string.chat_slash_command_missing_argument
                            SlashCommandError.InvalidArgument -> R.string.chat_slash_command_invalid_argument
                        },
                        "/" + parsed.token,
                    )
                    is SlashCommandParseResult.Parsed -> runCatching { executeSlashCommand(parsed.command) }
                        .onFailure { failure -> postError(failure.message ?: appContext.getString(R.string.chat_slash_command_unsupported, "/" + parsed.command.name)) }
                }
            } finally {
                _isSending.value = false
            }
        }
    }

    private suspend fun composeOutgoingText(rawText: String): String {
        if (rawText.trimStart().startsWith("/")) return rawText.trim()
        val pending = _pendingReply.value ?: return rawText.trim()
        val style = appPreferences.replyStyle.first()
        val customTemplate = appPreferences.replyCustomTemplate.first()
        val prefix = buildReplyPrefix(style, customTemplate, pending.nick, pending.messageBody)
        return (prefix + rawText).trim()
    }

    private fun sendMessage(text: String, draftSnapshot: String = _draft.value) {
        // Set this synchronously before launching: a second tap can otherwise enqueue
        // another identical request while the first one awaits the network response.
        if (_isSending.value) return
        _isSending.value = true
        viewModelScope.launch {
            try {
                runCatching {
                    channelReady.await()
                    chatRepository.sendMessage(networkSlug, channelName, text).getOrThrow()
                }.onSuccess {
                    _scrollToLatestAfterSend.tryEmit(Unit)
                    if (_draft.value.trim() == draftSnapshot.trim()) {
                        setDraft("")
                        _pendingReply.value = null
                    }
                }.onFailure { postError(it.message) }
            } finally {
                _isSending.value = false
            }
        }
    }

    /** User said "send it" in the multi-line flood-guard dialog. */
    fun confirmMultiLineSend() {
        val text = _pendingMultiLineSend.value ?: return
        _pendingMultiLineSend.value = null
        sendMultiline(text, _draft.value)
    }

    /** User cancelled the multi-line dialog — the draft stays untouched for editing. */
    fun dismissMultiLineSend() {
        _pendingMultiLineSend.value = null
    }

    // One PRIVMSG per line, awaited in order — the send-path half of
    // `MessageLines`, mirroring cicchetto's `sendBodyLines`. On a rate-limit the
    // same line is retried after the server's retry-after; on any other failure
    // the unsent remainder (the failed line onward) is mirrored back into the
    // draft so the operator loses nothing that has not gone out.
    private fun sendMultiline(text: String, draftSnapshot: String = _draft.value) {
        if (_isSending.value) return
        val lines = MessageLines.splitMessageLines(text)
        _isSending.value = true
        viewModelScope.launch {
            try {
                channelReady.await()
                var sent = 0
                while (sent < lines.size) {
                    val line = lines[sent]
                    val result = runCatching {
                        chatRepository.sendMessage(networkSlug, channelName, line).getOrThrow()
                    }
                    result.onFailure { failure ->
                        if (failure is RateLimitException) {
                            delay(failure.retryAfterMs ?: DEFAULT_RATE_LIMIT_RETRY_MS)
                            return@onFailure
                        }
                        postError(failure.message)
                    }
                    if (result.isSuccess) sent += 1
                }
                if (sent > 0) _scrollToLatestAfterSend.tryEmit(Unit)
                val residue = lines.drop(sent)
                if (residue.isNotEmpty()) {
                    setDraft(residue.joinToString("\n"))
                    if (sent > 0) _pendingReply.value = null
                } else if (_draft.value.trim() == draftSnapshot.trim()) {
                    setDraft("")
                    _pendingReply.value = null
                }
            } finally {
                _isSending.value = false
            }
        }
    }

    private suspend fun executeSlashCommand(command: SlashCommand) {
        val args = command.arguments
        val argument = args.firstOrNull()
        when (command.name) {
            "me" -> {
                chatRepository.sendMessage(networkSlug, channelName, args.joinToString(" "), channelName).getOrThrow()
                setDraft("")
            }
            "join" -> {
                channelReady.await()
                networksRepository.joinChannel(networkSlug, requireNotNull(argument), args.getOrNull(1)).getOrThrow()
                setDraft("")
                _commandEffects.emit(ChatCommandEffect.OpenChannel(requireNotNull(argument)))
            }
            "part" -> {
                val target = argument?.takeIf(::isChannelName) ?: channelName
                if (!isChannelName(target)) {
                    // Closing a DM window, not leaving a channel — a REST PART on a
                    // nick is meaningless, so use the query-window verb plus the same
                    // optimistic local removal as Home's close action.
                    val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                    membersRepository.closeQueryWindow(subject, networkId, target)
                    networksRepository.closeLocalQuery(networkSlug, target)
                    setDraft("")
                    _commandEffects.emit(ChatCommandEffect.CloseChat)
                } else {
                    val reason = if (argument != null && isChannelName(argument)) args.drop(1) else args
                    networksRepository.partChannel(networkSlug, target, reason.joinToString(" ").ifBlank { null }).getOrThrow()
                    setDraft("")
                    if (canonicalTarget(target) == canonicalTarget(channelName)) _commandEffects.emit(ChatCommandEffect.CloseChat)
                }
            }
            "cycle" -> {
                val target = argument?.takeIf(::isChannelName) ?: channelName
                val reason = if (argument != null && isChannelName(argument)) args.drop(1) else args
                networksRepository.partChannel(networkSlug, target, reason.joinToString(" ").ifBlank { null }).getOrThrow()
                networksRepository.joinChannel(networkSlug, target).getOrThrow()
                setDraft("")
                _commandEffects.emit(ChatCommandEffect.OpenChannel(target))
            }
            "topic" -> {
                check(!isQueryTarget(channelName)) { appContext.getString(R.string.chat_slash_channel_only) }
                networksRepository.updateTopic(networkSlug, channelName, if (argument == "-delete") "" else args.joinToString(" ")).getOrThrow()
                setDraft("")
            }
            "nick" -> {
                networksRepository.changeNick(networkSlug, requireNotNull(argument)).getOrThrow()
                setDraft("")
            }
            "msg" -> {
                val target = requireNotNull(argument)
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                // Services replies land on $server, never in a query window — don't
                // open one (cicchetto's isServicesSender short-circuit).
                if (!isServiceNick(target)) {
                    membersRepository.openQueryWindow(subject, networkId, target)
                    chatRepository.sendMessage(networkSlug, target, args.drop(1).joinToString(" ")).getOrThrow()
                } else {
                    chatRepository.sendServiceMessage(networkSlug, target, args.drop(1).joinToString(" ")).getOrThrow()
                }
                setDraft("")
                if (!isServiceNick(target)) {
                    _commandEffects.emit(ChatCommandEffect.OpenChannel(target))
                }
            }
            "query" -> {
                contactPrivately(requireNotNull(argument))
                setDraft("")
            }
            "whois" -> {
                requestWhois(requireNotNull(argument))
                setDraft("")
            }
            "whowas" -> {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.requestWhowas(subject, networkId, requireNotNull(argument))
                setDraft("")
            }
            "who" -> {
                // Full rest passed through (masks/flags preserved, like cicchetto);
                // bare defaults to the current channel, which must be a real channel.
                val target = args.joinToString(" ").ifBlank { channelName }
                if (target == channelName) {
                    check(!isQueryTarget(channelName)) { appContext.getString(R.string.chat_slash_channel_only) }
                }
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.requestWho(subject, networkId, target)
                setDraft("")
            }
            "lusers" -> {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                lusersRequested = true
                membersRepository.requestLusers(subject, networkId, args.getOrNull(0), args.getOrNull(1))
                setDraft("")
            }
            "links" -> {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.requestLinks(subject, networkId, args.joinToString(" ").ifBlank { null })
                setDraft("")
            }
            "info" -> {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.requestInfo(subject, networkId)
                setDraft("")
            }
            "version" -> {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.requestVersion(subject, networkId)
                setDraft("")
            }
            "motd" -> {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.requestMotd(subject, networkId, args.joinToString(" ").ifBlank { null })
                setDraft("")
            }
            "admin" -> {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.requestAdmin(subject, networkId, args.joinToString(" ").ifBlank { null })
                setDraft("")
            }
            "recover" -> {
                // Bare /recover uses this chat's network; `/recover <network>`
                // targets a named one (same resolver shape as connect).
                val targetSlug = argument?.takeIf { s ->
                    availableNetworks.value.any { it.equals(s, ignoreCase = true) }
                } ?: networkSlug
                val networkId = checkNotNull(networksRepository.networkIdForSlug(targetSlug))
                membersRepository.requestRecover(subject, networkId)
                setDraft("")
            }
            "names" -> {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.requestNames(subject, networkId, requireNotNull(argument))
                setDraft("")
            }
            "op", "deop", "voice", "devoice" -> {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.setNickModes(subject, networkId, channelName, command.name, args)
                setDraft("")
            }
            "kick" -> {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.kick(subject, networkId, channelName, requireNotNull(argument), args.drop(1).joinToString(" ").ifBlank { null })
                setDraft("")
            }
            "kb" -> {
                // Same two-push sequence as cicchetto's kbCommand (ban first, then
                // kick — "atomic" only in the sense that no rejoin slips between
                // them when both succeed): resolve the nick to *!*@host from the
                // server's userhost cache, and kick anyway on a cache miss.
                check(!isQueryTarget(channelName)) { appContext.getString(R.string.chat_slash_channel_only) }
                val target = requireNotNull(argument)
                val reason = args.drop(1).joinToString(" ").ifBlank { null }
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                val userhost = membersRepository.resolveUserhost(subject, networkId, target)
                if (userhost != null) {
                    membersRepository.ban(subject, networkId, channelName, "*!*@${userhost.host}")
                }
                membersRepository.kick(subject, networkId, channelName, target, reason)
                if (userhost == null) {
                    showSlashError(R.string.chat_slash_kb_host_unknown, target)
                }
                setDraft("")
            }
            "ban" -> {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.ban(subject, networkId, channelName, requireNotNull(argument))
                setDraft("")
            }
            "unban" -> {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                membersRepository.unban(subject, networkId, channelName, requireNotNull(argument))
                setDraft("")
            }
            "banlist" -> {
                _commandEffects.emit(ChatCommandEffect.OpenChannelSettings)
                setDraft("")
            }
            "ignore" -> {
                ignoresRepository.addIgnore(networkSlug, requireNotNull(argument)).getOrThrow()
                setDraft("")
            }
            "unignore" -> {
                ignoresRepository.removeIgnore(networkSlug, requireNotNull(argument)).getOrThrow()
                setDraft("")
            }
            "invite" -> {
                val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                val targetChannel = args.getOrNull(1)?.takeIf(::isChannelName) ?: channelName
                membersRepository.invite(subject, networkId, targetChannel, requireNotNull(argument))
                setDraft("")
            }
            "umode" -> {
                if (args.isEmpty()) {
                    openUmodeModal(networkSlug)
                    setDraft("")
                } else {
                    val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                    membersRepository.setUmode(subject, networkId, args.joinToString(" "))
                    setDraft("")
                }
            }
            "mode" -> {
                // `/mode <ownnick>` with no mode args opens the umode viewer —
                // there is no viewer for another user's modes.
                val ownNick = myNick.value ?: username
                if (args.size == 1 && args[0].equals(ownNick, ignoreCase = true)) {
                    openUmodeModal(networkSlug)
                    setDraft("")
                    return
                }
                if (args.size >= 2 && args[0].equals(ownNick, ignoreCase = true)) {
                    val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                    membersRepository.setUmode(subject, networkId, args.drop(1).joinToString(" "))
                    setDraft("")
                    return
                }
                if (args.isEmpty()) {
                    _commandEffects.emit(ChatCommandEffect.OpenChannelSettings)
                } else {
                    val target = args.first().takeIf(::isChannelName) ?: channelName
                    val modeIndex = if (target == channelName) 0 else 1
                    val modes = args.getOrNull(modeIndex) ?: error(appContext.getString(R.string.chat_slash_command_missing_argument, "/mode"))
                    val networkId = checkNotNull(networksRepository.networkIdForSlug(networkSlug))
                    membersRepository.setMode(subject, networkId, target, modes, args.drop(modeIndex + 1))
                }
                setDraft("")
            }
            "notify" -> {
                networksRepository.addNotify(networkSlug, args)
                setDraft("")
            }
            "away" -> {
                val reason = args.joinToString(" ").ifBlank { null }
                if (reason == null) {
                    membersRepository.unsetAway(subject, networkSlug)
                } else {
                    // Going away clears this network's stale mentions digest HERE,
                    // on the operator's own action — not on the away_confirmed
                    // echo (cicchetto #268: a delayed echo could otherwise wipe
                    // a fresh bundle the return already delivered).
                    membersRepository.clearMentions(networkSlug)
                    membersRepository.setAway(subject, networkSlug, reason)
                }
                setDraft("")
            }
            "hilight", "dehilight" -> {
                // Whole rest = one pattern (spaces allowed), like cicchetto; bare
                // opens settings where the full list is managed.
                if (args.isEmpty()) {
                    _commandEffects.emit(ChatCommandEffect.OpenAppSettings)
                } else {
                    val pattern = args.joinToString(" ")
                    val updated = if (command.name == "hilight") {
                        userSettingsRepository.addHighlight(subject, pattern).getOrThrow()
                    } else {
                        userSettingsRepository.removeHighlight(subject, pattern).getOrThrow()
                    }
                    _highlightNotice.value = appContext.getString(
                        R.string.chat_slash_hilight_list,
                        updated.size,
                        updated.joinToString(", "),
                    )
                }
                setDraft("")
            }
            "ns", "cs", "ms", "os", "hs", "rs" -> {
                // Raw PRIVMSG to the service nick over REST — no query window, no
                // focus switch (replies land on $server via the services-sender
                // allowlist, same as cicchetto). Bare sends `help` and opens $server
                // instead of cicchetto's confined help modal — same content, one less
                // custom surface.
                val service = checkNotNull(serviceNickFor(command.name))
                val body = args.joinToString(" ").ifBlank { "help" }
                chatRepository.sendServiceMessage(networkSlug, service, body).getOrThrow()
                setDraft("")
                if (args.isEmpty()) {
                    _commandEffects.emit(ChatCommandEffect.OpenChannel("\$server"))
                }
            }
            "credits" -> {
                _showCredits.value = true
                setDraft("")
            }
            "alias" -> {
                if (args.isEmpty()) {
                    _commandEffects.emit(ChatCommandEffect.OpenAppSettings)
                } else {
                    val name = requireNotNull(argument).lowercase()
                    check(slashCommandCatalog.none { it.name.equals(name, true) || it.aliases.any { alias -> alias.equals(name, true) } }) { appContext.getString(R.string.chat_slash_alias_builtin) }
                    val aliases = userSettingsRepository.getAliases().getOrThrow().toMutableMap()
                    aliases[name] = args.drop(1).joinToString(" ")
                    userSettingsRepository.updateAliases(aliases).getOrThrow()
                    setDraft("")
                }
            }
            "unalias" -> {
                val aliases = userSettingsRepository.getAliases().getOrThrow().toMutableMap()
                aliases.remove(requireNotNull(argument).lowercase())
                userSettingsRepository.updateAliases(aliases).getOrThrow()
                setDraft("")
            }
            "connect", "disconnect", "reconnect" -> {
                val (targetNetwork, reason) = resolveNetworkAndReason(args, command.name == "connect")
                when (command.name) {
                    "connect" -> networksRepository.updateConnectionState(targetNetwork, true, reason.ifBlank { null }).getOrThrow()
                    "disconnect" -> networksRepository.updateConnectionState(targetNetwork, false, reason.ifBlank { null }).getOrThrow()
                    else -> {
                        networksRepository.updateConnectionState(targetNetwork, false, reason.ifBlank { null }).getOrThrow()
                        networksRepository.updateConnectionState(targetNetwork, true, reason.ifBlank { null }).getOrThrow()
                    }
                }
                if (reason.isNotBlank()) Unit
                setDraft("")
            }
            "quit" -> {
                val reason = args.joinToString(" ").ifBlank { null }
                networksRepository.networksWithChannels.first().forEach { networksRepository.updateConnectionState(it.network.slug, false, reason).getOrThrow() }
                authRepository.detach()
                setDraft("")
            }
            else -> showSlashError(R.string.chat_slash_command_unsupported, "/${command.name}")
        }
    }

    private fun isChannelName(value: String): Boolean = value.firstOrNull() in setOf('#', '&', '+', '!')

    private fun resolveNetworkAndReason(args: List<String>, networkRequired: Boolean): Pair<String, String> {
        val candidate = args.firstOrNull()
        val isNetwork = candidate != null && availableNetworks.value.any { it.equals(candidate, ignoreCase = true) }
        if (networkRequired && !isNetwork) error(appContext.getString(R.string.chat_slash_command_invalid_argument, "/connect"))
        return if (isNetwork) candidate!! to args.drop(1).joinToString(" ") else networkSlug to args.joinToString(" ")
    }

    private fun showSlashError(messageRes: Int, argument: String? = null) {
        postError(
            if (argument == null) {
                appContext.getString(messageRes)
            } else {
                appContext.getString(messageRes, argument)
            },
        )
    }

    // Error snackbar auto-dismiss: without this a failure (e.g. a command the
    // server refused) sits on screen until the next successful send, which reads
    // as "stuck". Newer errors win — a delayed clear never wipes a fresher one.
    private var errorGeneration = 0

    private fun postError(message: String?) {
        if (message == null) return
        _error.value = message
        val generation = ++errorGeneration
        viewModelScope.launch {
            delay(6_000)
            if (errorGeneration == generation) _error.value = null
        }
    }

    fun uploadFile(uri: Uri) {
        viewModelScope.launch {
            runCatching { channelReady.await() }
                .onFailure { postError(it.message); return@launch }
            val meta = readUploadMeta(appContext, uri)
                ?: run { postError(appContext.getString(R.string.chat_upload_failed)); return@launch }
            if (uploadConfirmEnabled && _pendingUpload.value == null) {
                // #1883 — stage behind the confirm dialog instead of sending.
                // A second pick arriving while one is staged (e.g. a multi-file
                // OS share) goes straight out: the dialog holds a single file.
                _pendingUpload.value = PendingUploadConfirm(
                    uri = meta.uri,
                    fileName = meta.fileName,
                    sizeBytes = meta.sizeBytes,
                    mimeType = meta.mimeType,
                    ttlSeconds = effectiveUploadTtlSeconds(uploadTtlPreference),
                )
            } else {
                sendUpload(meta.uri, uploadTtlPreference.takeIf { it in UPLOAD_TTL_LADDER_SECONDS })
            }
        }
    }

    fun onPendingUploadTtlChange(seconds: Int) {
        _pendingUpload.value = _pendingUpload.value?.copy(ttlSeconds = seconds)
    }

    /** The dialog's Send: uploads the staged file with the chosen TTL. The
     * choice is per-batch only — it is NOT written back to the stored
     * preference (same as cicchetto: a one-off stays a one-off). */
    fun confirmPendingUpload() {
        val pending = _pendingUpload.value ?: return
        _pendingUpload.value = null
        sendUpload(pending.uri, pending.ttlSeconds)
    }

    fun dismissPendingUpload() {
        _pendingUpload.value = null
    }

    private fun sendUpload(uri: Uri, expireSeconds: Int?) {
        viewModelScope.launch {
            runCatching {
                _isUploading.value = true
                val pending = readUploadFile(appContext, uri)
                    ?: error(appContext.getString(R.string.chat_upload_failed))
                chatRepository.uploadAndSend(networkSlug, channelName, pending.bytes, pending.fileName, pending.mimeType, expireSeconds)
                    .getOrThrow()
            }.onFailure { postError(it.message) }
            _isUploading.value = false
        }
    }

    /** Manual "reload the buffer" — the only *user-driven* recourse when a backfill
     * silently fell behind (a gap wider than
     * [pm.antani.resentin.domain.repository.ChatRepository]'s drain cap). See
     * [onResumed] for the automatic counterpart. */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            chatRepository.backfill(networkSlug, channelName).onFailure { postError(it.message) }
            _isRefreshing.value = false
        }
    }

    /** Automatic counterpart to [refresh], fired whenever this chat screen becomes
     * visible again (`ON_RESUME`) — including "the screen was off while this chat
     * stayed open the whole time". `init`'s own backfill only ever runs once, when
     * this ViewModel is first created; staying on the same chat across a screen-off
     * cycle never recreates it, so without this the live WS feed is the ONLY way new
     * messages arrive, and a connection that silently died while asleep (a dropped
     * radio during Doze, a socket that never got a chance to notice and reconnect)
     * left the chat frozen at whatever it last saw until the user noticed and pulled
     * to refresh. Silent (no spinner, no error banner) since it fires on every
     * resume — including the unremarkable common case where nothing was missed at
     * all — not just the rare one this exists for. */
    fun onResumed() {
        viewModelScope.launch {
            chatRepository.backfill(networkSlug, channelName)
        }
    }

    fun loadOlder() {
        if (_isLoadingOlder.value) return
        viewModelScope.launch {
            _isLoadingOlder.value = true
            chatRepository.loadOlder(networkSlug, channelName)
                .onFailure { postError(it.message) }
            _isLoadingOlder.value = false
        }
    }

    companion object {
        /** Flood-guard threshold: drafts that split into MORE than this many
         * messages confirm before sending. Mirrors cicchetto's #80 carve-out:
         * short pastes (1–3 lines) stay frictionless, taller bursts ask. */
        const val MULTI_LINE_CONFIRM_MESSAGES = 3

        /** Fallback wait when a rate-limit reply carries no retry-after. */
        private const val DEFAULT_RATE_LIMIT_RETRY_MS = 2_000L

        fun factory(
            chatRepository: ChatRepository,
            networksRepository: NetworksRepository,
            membersRepository: MembersRepository,
            ignoresRepository: IgnoresRepository,
            authRepository: AuthRepository,
            userSettingsRepository: UserSettingsRepository,
            appPreferences: AppPreferences,
            connectionManager: ConnectionManager,
            openChatTracker: OpenChatTracker,
            pendingShareHolder: PendingShareHolder,
            appContext: Context,
            networkSlug: String,
            channelName: String,
            username: String,
            subject: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(
                    chatRepository,
                    networksRepository,
                    membersRepository,
                    ignoresRepository,
                    authRepository,
                    userSettingsRepository,
                    appPreferences,
                    connectionManager,
                    openChatTracker,
                    pendingShareHolder,
                    appContext,
                    networkSlug,
                    channelName,
                    username,
                    subject,
                ) as T
            }
        }
    }
}
