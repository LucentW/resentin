package pm.antani.resentin.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pm.antani.resentin.R
import pm.antani.resentin.data.db.MessageEntity
import pm.antani.resentin.data.prefs.AppPreferences
import pm.antani.resentin.data.prefs.ChatDisplayMode
import pm.antani.resentin.domain.repository.ChatRepository
import pm.antani.resentin.domain.repository.MembersRepository
import pm.antani.resentin.domain.repository.NetworksRepository
import pm.antani.resentin.domain.session.ConnectionManager
import pm.antani.resentin.domain.session.OpenChatTracker
import pm.antani.resentin.domain.session.PendingShareHolder
import pm.antani.resentin.ui.common.UserCardController

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val networksRepository: NetworksRepository,
    membersRepository: MembersRepository,
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

    val messages: StateFlow<List<MessageEntity>> = chatRepository.observeMessages(networkSlug, channelName)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val topic: StateFlow<String?> = networksRepository.observeChannel(networkSlug, channelName)
        .map { it?.topic?.takeIf { topic -> topic.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val channelModes: StateFlow<String?> = networksRepository.observeChannel(networkSlug, channelName)
        .map { it?.modes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // The viewer's own current nick on this network — same source NotificationRouter
    // reads to decide whether an incoming message deserves a notification, reused here
    // (via irc.containsMention) so a message highlighted as "mentions you" in chat can
    // never drift from one that actually fired a notification.
    val myNick: StateFlow<String?> = networksRepository.observeNetwork(networkSlug)
        .map { it?.nick }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chatDisplayMode: StateFlow<ChatDisplayMode> = appPreferences.chatDisplayMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatDisplayMode.BUBBLES)

    val showSeconds: StateFlow<Boolean> = appPreferences.showSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val coloredNicklist: StateFlow<Boolean> = appPreferences.coloredNicklist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val showHostmaskInEvents: StateFlow<Boolean> = appPreferences.showHostmaskInEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Long-press-on-message user card: same whois/moderation logic as the member list,
    // scoped to this same (network, channel) — empty members/sigils for a query/$server,
    // which naturally hides the channel-only actions in the card.
    private val userCard =
        UserCardController(membersRepository, networksRepository, networkSlug, channelName, username, subject, viewModelScope)
    val selectedWhois = userCard.selectedWhois
    val ownSigils = userCard.ownSigils
    val privilegeModes = userCard.privilegeModes
    val navigateToQuery = userCard.navigateToQuery
    // Exposed for the nick role-prefix (~&@%+) shown in both chat display modes.
    val members = userCard.members

    fun onMessageLongPress(nick: String) = userCard.onNickClick(nick)
    fun dismissWhois() = userCard.dismissWhois()
    fun kickFromCard(nick: String) = userCard.kick(nick)
    fun banFromCard(nick: String) = userCard.ban(nick)
    fun contactPrivately(nick: String) = userCard.contactPrivately(nick)
    fun setModeFromCard(nick: String, letter: Char, grant: Boolean) = userCard.setMode(nick, letter, grant)
    fun sigilsFor(nick: String) = userCard.sigilsFor(nick)

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isLoadingOlder = MutableStateFlow(false)
    val isLoadingOlder: StateFlow<Boolean> = _isLoadingOlder.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Snapshot of "where the reader left off" BEFORE this session marks anything new as
    // read — the one-time scroll target. Null until known; ChatScreen should wait for a
    // non-null-or-otherwise-settled value before deciding where to land initially.
    private val _initialReadCursor = MutableStateFlow<Long?>(null)
    val initialReadCursor: StateFlow<Long?> = _initialReadCursor.asStateFlow()

    // Distinguishes "not known yet" from "known to be null" (never read) — the UI must
    // wait for this before deciding where to scroll, or it'll always land on the bottom.
    private val _initialReadCursorReady = MutableStateFlow(false)
    val initialReadCursorReady: StateFlow<Boolean> = _initialReadCursorReady.asStateFlow()

    private var lastMarkedRead = 0L

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    // One-shot signal for ChatScreen to grab keyboard focus (and move the cursor to the
    // end of the now-prefilled draft) right after a swipe-to-reply — reply() alone only
    // changes the draft's TEXT, which doesn't imply focus or cursor position on its own.
    private val _replyFocusRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val replyFocusRequests: SharedFlow<Unit> = _replyFocusRequests.asSharedFlow()

    init {
        openChatTracker.onChatOpened(networkSlug, channelName)
        // Arrived here via the Android share sheet (ShareTargetScreen staged the URIs
        // before navigating in) — upload each one now, exactly like tapping the attach
        // button once per file. Consumed once so re-entering this chat later doesn't
        // re-upload the same share.
        pendingShareHolder.consume().forEach { uploadFile(it) }
        viewModelScope.launch {
            userCard.error.collect { message -> message?.let { _error.value = it } }
        }
        viewModelScope.launch {
            _initialReadCursor.value = networksRepository.getStoredReadCursor(networkSlug, channelName)
            runCatching {
                connectionManager.connect()
                connectionManager.joinChannel("grappa:user:$subject")
                connectionManager.joinChannel("grappa:user:$subject/network:$networkSlug")
                val channelTopic = "grappa:user:$subject/network:$networkSlug/channel:$channelName"
                networksRepository.applyJoinResponse(channelTopic, connectionManager.joinChannel(channelTopic))
                // A join reply only happens once per process lifetime per topic (usually
                // AppContainer's app-wide join already consumed it before this screen
                // even opened, via the same applyJoinResponse path) — if we still have no
                // scroll target by then, fall back to whatever Room ended up with.
                if (_initialReadCursor.value == null) {
                    _initialReadCursor.value = networksRepository.getStoredReadCursor(networkSlug, channelName)
                }
            }.onFailure { _error.value = it.message }
            // Only now, not before backfill() — while it's running, `messages` is a
            // partial, arbitrarily-ordered-by-arrival prefix of the true history (Room
            // emits on every individual upsert), so indexOfFirst{it.id > cursor} against
            // that partial snapshot can match a much-too-early row and then latch onto
            // it forever (ChatScreen sets hasScrolledInitially on its first pass once
            // this flips true). Waiting for backfill's suspend call to actually return
            // guarantees the full page is committed before that first pass runs.
            chatRepository.backfill(networkSlug, channelName).onFailure { _error.value = it.message }
            _initialReadCursorReady.value = true
        }
        // Marks the newest loaded message as read whenever it changes — the chat being
        // open (this ViewModel existing) is already the "the user is looking at this"
        // signal the rest of the app (OpenChatTracker) relies on.
        viewModelScope.launch {
            messages.collect { list -> list.maxByOrNull { it.id }?.let { markRead(it.id) } }
        }
    }

    private fun markRead(messageId: Long) {
        if (messageId <= lastMarkedRead) return
        lastMarkedRead = messageId
        viewModelScope.launch {
            chatRepository.markRead(networkSlug, channelName, messageId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        openChatTracker.onChatClosed(networkSlug, channelName)
    }

    fun onDraftChange(text: String) {
        _draft.value = text
    }

    /** Swipe-to-reply: prefills the draft using the active reply-style template
     * (Settings) — plain `nick: ` by default, optionally a quoted preview of the
     * original message, or a fully custom template. IRC has no real threaded replies,
     * this is just the addressing/quoting convention most bouncers highlight on. */
    fun reply(nick: String, messageBody: String) {
        viewModelScope.launch {
            val style = appPreferences.replyStyle.first()
            val customTemplate = appPreferences.replyCustomTemplate.first()
            val prefix = buildReplyPrefix(style, customTemplate, nick, messageBody)
            if (!_draft.value.startsWith(prefix)) _draft.value = prefix + _draft.value
            _replyFocusRequests.tryEmit(Unit)
        }
    }

    fun send() {
        val text = _draft.value.trim()
        if (text.isBlank()) return
        _draft.value = ""
        viewModelScope.launch {
            chatRepository.sendMessage(networkSlug, channelName, text)
                .onFailure { _error.value = it.message }
        }
    }

    fun uploadFile(uri: Uri) {
        viewModelScope.launch {
            _isUploading.value = true
            runCatching {
                val pending = readUploadFile(appContext, uri)
                    ?: error(appContext.getString(R.string.chat_upload_failed))
                chatRepository.uploadAndSend(networkSlug, channelName, pending.bytes, pending.fileName, pending.mimeType)
                    .getOrThrow()
            }.onFailure { _error.value = it.message }
            _isUploading.value = false
        }
    }

    /** Manual "reload the buffer" — the only recourse when a backfill silently fell
     * behind (a gap wider than [pm.antani.resentin.domain.repository.ChatRepository]'s
     * drain cap) and there's no other trigger to try again from. */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            chatRepository.backfill(networkSlug, channelName).onFailure { _error.value = it.message }
            _isRefreshing.value = false
        }
    }

    fun loadOlder() {
        if (_isLoadingOlder.value) return
        viewModelScope.launch {
            _isLoadingOlder.value = true
            chatRepository.loadOlder(networkSlug, channelName)
                .onFailure { _error.value = it.message }
            _isLoadingOlder.value = false
        }
    }

    companion object {
        fun factory(
            chatRepository: ChatRepository,
            networksRepository: NetworksRepository,
            membersRepository: MembersRepository,
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
