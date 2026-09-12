package pm.antani.resentin.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import pm.antani.resentin.R
import pm.antani.resentin.data.db.MemberEntity
import pm.antani.resentin.data.db.MessageEntity
import pm.antani.resentin.data.prefs.ChatDisplayMode
import pm.antani.resentin.data.prefs.MessageDensity
import pm.antani.resentin.irc.FormattedEvent
import pm.antani.resentin.irc.SystemEventFormatter
import pm.antani.resentin.irc.containsMention
import pm.antani.resentin.irc.matchesHighlight
import pm.antani.resentin.irc.MessageLines
import pm.antani.resentin.net.dto.LusersBundleDto
import pm.antani.resentin.net.dto.WhoReplyDto
import pm.antani.resentin.net.dto.WhowasBundleDto
import pm.antani.resentin.irc.highestSigil
import pm.antani.resentin.net.AppJson
import pm.antani.resentin.ui.common.MircText
import pm.antani.resentin.ui.common.rememberAvatarBitmap
import pm.antani.resentin.ui.common.ResentinDropdownMenu
import pm.antani.resentin.ui.common.ResentinDropdownMenuItem
import pm.antani.resentin.ui.common.ResentinHeaderAction
import pm.antani.resentin.ui.common.ResentinEmptyState
import pm.antani.resentin.ui.common.ResentinLoadingState
import pm.antani.resentin.ui.common.UserCardSheet
import pm.antani.resentin.ui.common.colorForNick
import pm.antani.resentin.ui.common.isLightTheme
import pm.antani.resentin.ui.common.linkStylesFor
import pm.antani.resentin.ui.common.mircAnnotatedString
import pm.antani.resentin.ui.common.sigilsOf
import pm.antani.resentin.ui.common.withClickableLinks
import pm.antani.resentin.ui.theme.LocalResentinCodeFontFamily

/**
 * Moves to the final row without LazyColumn's long-distance item-by-item spring.
 * That default animation can visibly pause while new rows are measured. Small,
 * timed pixel chunks keep the motion continuous; the final snap is only a residual
 * correction after the bottom anchor has been composed.
 */
private suspend fun LazyListState.animateToChatBottom() {
    repeat(8) {
        val layoutInfo = layoutInfo
        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull() ?: return
        val targetIndex = layoutInfo.totalItemsCount - 1
        if (targetIndex < 0) return

        val lastItemEnd = lastVisible.offset + lastVisible.size
        if (lastVisible.index >= targetIndex && lastItemEnd <= layoutInfo.viewportEndOffset) return

        val averageItemSize = layoutInfo.visibleItemsInfo
            .map { it.size }
            .average()
            .toFloat()
            .coerceAtLeast(1f)
        val remainingItems = (targetIndex - lastVisible.index).coerceAtLeast(0)
        val clippedTail = (lastItemEnd - layoutInfo.viewportEndOffset).coerceAtLeast(0)
        val estimatedDistance = remainingItems * averageItemSize + clippedTail
        val distance = estimatedDistance.coerceIn(240f, 1400f)
        val duration = (distance / 4f).roundToInt().coerceIn(120, 260)
        animateScrollBy(
            value = distance,
            animationSpec = tween(durationMillis = duration, easing = LinearOutSlowInEasing),
        )
    }

    layoutInfo.totalItemsCount.takeIf { it > 0 }?.let { scrollToItem(it - 1) }
}

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
private val TIME_FORMATTER_WITH_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun formatTime(epochMillis: Long, showSeconds: Boolean): String {
    val formatter = if (showSeconds) TIME_FORMATTER_WITH_SECONDS else TIME_FORMATTER
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    title: String,
    networkSlug: String,
    channelName: String,
    viewerUsername: String,
    isQuery: Boolean = false,
    isServer: Boolean = false,
    onBack: () -> Unit,
    onMembersClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAppSettings: () -> Unit = {},
    onOpenQuery: (networkSlug: String, nick: String) -> Unit,
    onOpenChannel: (networkSlug: String, channelName: String) -> Unit,
) {
    val messages by viewModel.messages.collectAsState()
    val topic by viewModel.topic.collectAsState()
    val channelModes by viewModel.channelModes.collectAsState()
    val peerAvatarUrl by viewModel.peerAvatarUrl.collectAsState()
    val peerAvatarBitmap = rememberAvatarBitmap(peerAvatarUrl, viewModel::fetchAvatarBytes)
    val draft by viewModel.draft.collectAsState()
    // Local TextFieldValue (not just the String from the ViewModel) so an externally
    // triggered draft change — a swipe-to-reply prefill, or send() clearing it — can
    // explicitly place the cursor, instead of leaning on Compose's default "diff the new
    // text against whatever selection happened to be there" behavior, which does NOT
    // reliably land the cursor at the end (the reported bug: reply prefilled the text but
    // the caret stayed wherever it last was, not focused, not at the end).
    var draftFieldValue by remember { mutableStateOf(TextFieldValue(draft)) }
    val draftFocusRequester = remember { FocusRequester() }
    LaunchedEffect(draft) {
        if (draftFieldValue.text != draft) {
            draftFieldValue = TextFieldValue(text = draft, selection = TextRange(draft.length))
        }
    }
    LaunchedEffect(Unit) {
        viewModel.replyFocusRequests.collect { draftFocusRequester.requestFocus() }
    }
    val error by viewModel.error.collectAsState()
    val pendingMultiLineSend by viewModel.pendingMultiLineSend.collectAsState()
    val whois by viewModel.selectedWhois.collectAsState()
    val ownSigils by viewModel.ownSigils.collectAsState()
    val privilegeModes by viewModel.privilegeModes.collectAsState()
    val initialReadCursor by viewModel.initialReadCursor.collectAsState()
    val readCursor by viewModel.readCursor.collectAsState()
    val initialReadCursorReady by viewModel.initialReadCursorReady.collectAsState()
    val initialHistoryReady by viewModel.initialHistoryReady.collectAsState()
    val members by viewModel.members.collectAsState()
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSearchIndex by remember { mutableStateOf(0) }
    val searchMatches = remember(messages, searchQuery) {
        findLocalChatMatches(messages, searchQuery)
    }
    val selectedSearchMessageId = searchMatches.getOrNull(selectedSearchIndex)?.id
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(searchOpen) {
        if (searchOpen) searchFocusRequester.requestFocus()
    }
    LaunchedEffect(searchQuery) {
        selectedSearchIndex = 0
    }
    fun closeSearch() {
        searchOpen = false
        searchQuery = ""
        selectedSearchIndex = 0
    }

    LaunchedEffect(isServer) {
        if (isServer) closeSearch()
    }

    fun moveSearchResult(step: Int) {
        if (searchMatches.isEmpty()) return
        selectedSearchIndex = (selectedSearchIndex + step + searchMatches.size) % searchMatches.size
    }

    val activeMention = remember(draftFieldValue) { mentionQueryAtCursor(draftFieldValue) }
    val mentionSuggestions = remember(activeMention, members) {
        activeMention?.let { findMentionSuggestions(it.query, members) }.orEmpty()
    }
    val slashSuggestions = remember(draftFieldValue.text) {
        suggestSlashCommands(draftFieldValue.text)
    }
    val availableChannels by viewModel.availableChannels.collectAsState()
    val availableNetworks by viewModel.availableNetworks.collectAsState()
    val slashArgumentSuggestions = remember(draftFieldValue.text, members, availableChannels, availableNetworks) {
        suggestSlashArguments(
            draftFieldValue.text,
            members.map { it.nick },
            availableChannels,
            availableNetworks,
        )
    }

    fun completeMention(nick: String) {
        val mention = activeMention ?: return
        val before = draftFieldValue.text.substring(0, mention.start)
        val after = draftFieldValue.text.substring(mention.end)
        val inserted = nick + if (after.isEmpty() || !after.first().isWhitespace()) " " else ""
        val newText = before + inserted + after
        val newCursor = before.length + inserted.length
        val newValue = TextFieldValue(newText, TextRange(newCursor))
        draftFieldValue = newValue
        viewModel.onDraftChange(newText)
        draftFocusRequester.requestFocus()
    }

    fun completeSlashCommand(command: SlashCommandSpec) {
        val completion = completeSlashCommandInput(draftFieldValue.text, command)
        val newValue = TextFieldValue(completion.text, TextRange(completion.cursor))
        draftFieldValue = newValue
        viewModel.onDraftChange(completion.text)
        draftFocusRequester.requestFocus()
    }
    fun completeSlashArgument(suggestion: SlashArgumentSuggestion) {
        val completion = completeSlashArgumentInput(draftFieldValue.text, suggestion)
        val newValue = TextFieldValue(completion.text, TextRange(completion.cursor))
        draftFieldValue = newValue
        viewModel.onDraftChange(completion.text)
        draftFocusRequester.requestFocus()
    }
    val displayMode by viewModel.chatDisplayMode.collectAsState()
    val messageDensity by viewModel.messageDensity.collectAsState()
    val showSeconds by viewModel.showSeconds.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoadingOlder by viewModel.isLoadingOlder.collectAsState()
    val coloredNicklist by viewModel.coloredNicklist.collectAsState()
    val showHostmaskInEvents by viewModel.showHostmaskInEvents.collectAsState()
    val myNick by viewModel.myNick.collectAsState()
    val awayState by viewModel.awayState.collectAsState()
    val highlightPatterns by viewModel.highlightPatterns.collectAsState()
    val whowas by viewModel.whowas.collectAsState()
    val whoReply by viewModel.whoReply.collectAsState()
    val lusers by viewModel.lusers.collectAsState()
    val highlightNotice by viewModel.highlightNotice.collectAsState()
    val showCredits by viewModel.showCredits.collectAsState()
    var initialListIndex by remember { mutableStateOf<Int?>(null) }
    val positioned = initialListIndex != null
    val listState = remember(initialListIndex) {
        LazyListState(firstVisibleItemIndex = initialListIndex ?: 0)
    }
    val showHistoryLoading = messages.isEmpty() && (!initialHistoryReady || isRefreshing)
    val showHistoryError = messages.isEmpty() && initialHistoryReady && error != null && !isRefreshing
    // Do not briefly compose the list at index 0 and then jump to the unread divider.
    // The first LazyColumn is created with the final landing index already applied.
    var showTopicDialog by remember { mutableStateOf(false) }
    var showChannelMenu by remember { mutableStateOf(false) }
    // The long-pressed row's plain text, threaded to UserCardSheet for its "Copia"/
    // "Copia parziale" actions — the sheet itself only knows the sender's nick (it opens
    // off a WHOIS reply, which arrives async), not which message triggered it.
    var longPressedMessageText by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::uploadFile)
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToQuery.collect { nick -> onOpenQuery(networkSlug, nick) }
    }

    LaunchedEffect(Unit) {
        viewModel.commandEffects.collect { effect ->
            when (effect) {
                is ChatCommandEffect.OpenChannel -> onOpenChannel(networkSlug, effect.channelName)
                ChatCommandEffect.CloseChat -> onBack()
                ChatCommandEffect.OpenChannelSettings -> onSettingsClick()
                ChatCommandEffect.OpenAppSettings -> onAppSettings()
            }
        }
    }

    // Position (within `messages`) of the first message past where the reader left off —
    // also where the "Hai letto fino a qui" divider renders. During the first layout
    // use the frozen cursor so it remains a stable landing target. Once positioned,
    // switch to the live cursor so the divider disappears as soon as this chat is
    // confirmed read, without requiring a navigation away and back.
    // Null when there's nothing to mark (cursor still loading, never read anything, or
    // everything is already read).
    val dividerCursor = if (positioned) readCursor ?: initialReadCursor else initialReadCursor
    val dividerIndex = dividerCursor?.let { cursor ->
        messages.indexOfFirst { it.id > cursor }.takeIf { it >= 0 }
    }

    // List indices of the mention rows (own nick / /hilight match from another
    // sender — the SAME isMentionRow predicate the per-row highlight uses, the
    // way cicchetto's badge reads the rows the highlight marks). The unread
    // divider shifts every message row after it by one. Precomputed so the
    // scroll-path decision stays a cheap filter.
    val mentionRowIndices by remember(messages, dividerIndex, myNick, highlightPatterns, isQuery) {
        derivedStateOf {
            val divider = dividerIndex
            val indices = mutableListOf<Int>()
            messages.forEachIndexed { index, message ->
                if (isMentionRow(message, myNick, isQuery, highlightPatterns)) {
                    indices.add(index + if (divider != null && index >= divider) 1 else 0)
                }
            }
            indices
        }
    }

    LaunchedEffect(searchOpen, selectedSearchMessageId, messages.size, dividerIndex) {
        if (!searchOpen || selectedSearchMessageId == null) return@LaunchedEffect
        val messageIndex = messages.indexOfFirst { it.id == selectedSearchMessageId }
        if (messageIndex < 0) return@LaunchedEffect
        val listIndex = messageIndex + if (dividerIndex != null && messageIndex >= dividerIndex) 1 else 0
        listState.animateScrollToItem(listIndex)
    }

    // Land on the first unread message (per the server's read-cursor), not always the
    // bottom — only once, and only once we actually know where that is (see
    // ChatViewModel.initialReadCursorReady: null is ambiguous between "not loaded yet"
    // and "never read anything").
    LaunchedEffect(messages, initialReadCursorReady) {
        if (initialListIndex != null || !initialReadCursorReady || messages.isEmpty()) return@LaunchedEffect
        initialListIndex = dividerIndex ?: (messages.size - 1)
    }

    // Keep the bottom status based on the actual last composed row. In this screen
    // LazyColumn can report canScrollForward=false while it is still settling after
    // a large gesture, which leaves the jump button permanently hidden.
    var isAtBottom by remember { mutableStateOf(true) }
    var showJumpToBottom by remember { mutableStateOf(false) }
    // Below-the-fold mention rows for the jump badge (cicchetto #360 port). A
    // mention counts once its row lies entirely past the last laid-out row; one
    // straddling the fold is already seen. `mentionRowIndices` is a key here so
    // the badge picks up a new mention the moment it lands.
    var mentionBadgeCount by remember { mutableStateOf(0) }
    var nextMentionRowIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(listState, positioned, messages.isNotEmpty(), mentionRowIndices) {
        if (!positioned || messages.isEmpty()) {
            isAtBottom = true
            showJumpToBottom = false
            mentionBadgeCount = 0
            nextMentionRowIndex = null
            return@LaunchedEffect
        }

        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()
            val totalItems = info.totalItemsCount
            val lastVisibleIndex = lastVisible?.index ?: -1
            val lastVisibleEnd = lastVisible?.let { it.offset + it.size } ?: Int.MIN_VALUE
            val viewportEnd = info.viewportEndOffset
            Triple(totalItems, lastVisibleIndex, lastVisibleEnd <= viewportEnd)
        }.collect { (totalItems, lastVisibleIndex, lastVisibleIsFullyVisible) ->
            val atBottom = totalItems > 0 &&
                lastVisibleIndex >= totalItems - 1 &&
                lastVisibleIsFullyVisible
            isAtBottom = atBottom
            showJumpToBottom = !atBottom
            val below = MentionScroll.mentionRowsBelowFold(mentionRowIndices, lastVisibleIndex)
            mentionBadgeCount = below.size
            nextMentionRowIndex = below.firstOrNull()
        }
    }

    // Opening the IME reduces the list viewport, but it does not change the
    // messages list, so the regular new-message auto-follow effect below is
    // not triggered. Capture whether the user was following the tail when the
    // composer received focus and restore that position after the IME resizes
    // the layout. If the user was reading history, keep their position.
    var shouldScrollToBottomOnIme by remember { mutableStateOf(false) }
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(listState) {
        snapshotFlow {
            val bottomIndex = messages.size + if (dividerIndex != null) 1 else 0
            Triple(
                imeInsets.getBottom(density),
                shouldScrollToBottomOnIme && positioned,
                bottomIndex,
            )
        }.collectLatest { (imeBottom, shouldFollow, bottomIndex) ->
            if (imeBottom <= 0 || !shouldFollow || bottomIndex < 0) return@collectLatest
            // IME insets animate over multiple frames. Keep the tail aligned
            // after each inset change, once the current layout has measured.
            withFrameNanos { }
            listState.scrollToItem(bottomIndex)
        }
    }

    // Only auto-follow to the tail when the reader was already there — landing on the
    // unread divider (potentially far above the bottom, e.g. after a big backfill) must
    // NOT get yanked down the instant one more message arrives; a still-unread backlog
    // stays exactly where the reader put it. loadOlder() prepends at the head, which
    // doesn't change `lastOrNull()?.id`, so this never fires for that case either.
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (positioned && messages.isNotEmpty() && isAtBottom) {
            val bottomIndex = messages.size + if (dividerIndex != null) 1 else 0
            // During the initial REST fill, follow the cache with a snap. Once the
            // history is ready, live messages can use the normal smooth follow.
            if (initialHistoryReady) {
                listState.animateScrollToItem(bottomIndex)
            } else {
                listState.scrollToItem(bottomIndex)
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index -> if (index == 0 && messages.isNotEmpty()) viewModel.loadOlder() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                title = {
                    if (searchOpen) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                            placeholder = { Text(stringResource(R.string.chat_search_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isQuery && peerAvatarBitmap != null) {
                                Image(
                                    bitmap = peerAvatarBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Column(
                                modifier = Modifier.clickable(enabled = topic != null) { showTopicDialog = true },
                    ) {
                        Text(
                            text = if (!isQuery && !isServer) {
                                pluralStringResource(
                                    R.plurals.chat_channel_title_with_users,
                                    members.size,
                                    title,
                                    members.size,
                                )
                            } else {
                                title
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Seconda riga compatta: chip rete e Modi/topic affiancati
                        // su una sola riga (prima erano due righe separate), così
                        // l'header resta su due righe totali e lascia più spazio
                        // alla chat. "(+rnt) topic" segue la convenzione della
                        // status bar dei client IRC classici.
                        val subtitle = buildString {
                            if (channelModes != null) append("($channelModes) ")
                            if (topic != null) append(topic)
                        }.takeIf { it.isNotBlank() }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                ),
                            ) {
                                Text(
                                    networkSlug,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                            // Explicit /away state for this network — same presence
                            // signal cicchetto shows as a sidebar badge.
                            if (awayState == "away") {
                                Spacer(Modifier.size(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                ) {
                                    Text(
                                        stringResource(R.string.chat_away_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            if (subtitle != null) {
                                Spacer(Modifier.size(6.dp))
                                MircText(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                            }
                        }
                    }
                },
                actions = {
                    if (isServer) {
                        // The server conversation has no channel-specific header actions.
                    } else if (searchOpen) {
                        val counter = when {
                            searchQuery.isBlank() -> ""
                            searchMatches.isEmpty() -> stringResource(R.string.chat_search_no_results)
                            else -> stringResource(
                                R.string.chat_search_counter,
                                selectedSearchIndex + 1,
                                searchMatches.size,
                            )
                        }
                        if (counter.isNotEmpty()) {
                            Text(
                                text = counter,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { moveSearchResult(-1) },
                            enabled = searchMatches.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Outlined.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.cd_search_previous),
                            )
                        }
                        IconButton(
                            onClick = { moveSearchResult(1) },
                            enabled = searchMatches.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Outlined.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.cd_search_next),
                            )
                        }
                        IconButton(onClick = ::closeSearch) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cd_close_search),
                            )
                        }
                    } else if (isQuery) {
                        ResentinHeaderAction(
                            onClick = { searchOpen = true },
                            icon = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.cd_search_chat),
                        )
                        ResentinHeaderAction(
                            onClick = viewModel::refresh,
                            icon = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.cd_refresh),
                            enabled = !isRefreshing,
                            loading = isRefreshing,
                        )
                        // In a query, `title` IS the partner's nick (see AppRoot's
                        // ChatScreen call site) — the same nick onMessageLongPress
                        // already knows how to resolve into a WHOIS lookup.
                        ResentinHeaderAction(
                            onClick = { viewModel.onMessageLongPress(title) },
                            icon = Icons.Outlined.Person,
                            contentDescription = stringResource(R.string.cd_user_info),
                        )
                    } else {
                        ResentinHeaderAction(
                            onClick = { showChannelMenu = true },
                            icon = Icons.Outlined.MoreVert,
                            contentDescription = stringResource(R.string.cd_channel_menu),
                        )
                        ResentinDropdownMenu(
                            expanded = showChannelMenu,
                            onDismissRequest = { showChannelMenu = false },
                        ) {
                            ResentinDropdownMenuItem(
                                text = stringResource(R.string.cd_search_chat),
                                icon = Icons.Outlined.Search,
                                onClick = {
                                    showChannelMenu = false
                                    searchOpen = true
                                },
                            )
                            ResentinDropdownMenuItem(
                                text = stringResource(R.string.cd_refresh),
                                icon = Icons.Outlined.Refresh,
                                onClick = {
                                    showChannelMenu = false
                                    viewModel.refresh()
                                },
                            )
                            if (!isServer) {
                                ResentinDropdownMenuItem(
                                    text = stringResource(R.string.chat_channel_members_action),
                                    icon = Icons.Outlined.Group,
                                    onClick = {
                                        showChannelMenu = false
                                        onMembersClick()
                                    },
                                )
                            }
                            ResentinDropdownMenuItem(
                                text = stringResource(R.string.cd_channel_settings),
                                icon = Icons.Outlined.Settings,
                                onClick = {
                                    showChannelMenu = false
                                    onSettingsClick()
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                if (slashSuggestions.isNotEmpty()) {
                    SlashCommandSuggestions(
                        suggestions = slashSuggestions,
                        onSelect = ::completeSlashCommand,
                    )
                }
                if (slashArgumentSuggestions.isNotEmpty()) {
                    SlashArgumentSuggestions(
                        suggestions = slashArgumentSuggestions,
                        onSelect = ::completeSlashArgument,
                    )
                }
                if (mentionSuggestions.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 3.dp,
                    ) {
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            mentionSuggestions.forEach { member ->
                                item(key = "mention-${member.nick}") {
                                    DropdownMenuItem(
                                        text = { Text(member.nick) },
                                        onClick = { completeMention(member.nick) },
                                    )
                                }
                            }
                        }
                    }
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    ),
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                IconButton(
                    onClick = { filePicker.launch("*/*") },
                    enabled = !isUploading,
                    modifier = Modifier.size(40.dp),
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.AttachFile, contentDescription = stringResource(R.string.cd_attach_file))
                    }
                }
                TextField(
                    value = draftFieldValue,
                    onValueChange = { newValue ->
                        draftFieldValue = newValue
                        viewModel.onDraftChange(newValue.text)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(draftFocusRequester)
                        .onFocusChanged { focusState ->
                            shouldScrollToBottomOnIme = if (focusState.isFocused) {
                                positioned && isAtBottom
                            } else {
                                false
                            }
                        },
                    placeholder = { Text(stringResource(R.string.chat_message_placeholder)) },
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
                IconButton(
                    onClick = viewModel::send,
                    enabled = !isSending,
                    modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Outlined.Send,
                            contentDescription = stringResource(R.string.cd_send),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                showHistoryLoading -> {
                    ResentinLoadingState(
                        title = stringResource(R.string.chat_history_loading),
                        description = stringResource(R.string.chat_history_loading_description),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                showHistoryError -> {
                    ResentinEmptyState(
                        icon = Icons.Outlined.WifiOff,
                        title = stringResource(R.string.chat_history_error_title),
                        description = stringResource(R.string.chat_history_error_description),
                        actionLabel = stringResource(R.string.chat_history_retry),
                        onAction = viewModel::refresh,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                messages.isEmpty() -> {
                    ResentinEmptyState(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        title = stringResource(R.string.chat_history_empty_title),
                        description = stringResource(R.string.chat_history_empty_description),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                !positioned -> {
                    // Cached messages may already be available while the read cursor or
                    // backfill is settling. Keep the content area stable until the list
                    // state has been positioned, avoiding the visible top-to-unread jump.
                }
                else -> {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                messages.forEachIndexed { index, message ->
                    if (index == dividerIndex) {
                        item(key = "unread-divider") { UnreadDivider(density = messageDensity) }
                    }
                    item(key = message.id) {
                        val previous = messages.getOrNull(index - 1)
                        val tight = previous != null &&
                            previous.sender.equals(message.sender, ignoreCase = true) &&
                            previous.kind == message.kind
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (message.id == selectedSearchMessageId) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                                    } else {
                                        androidx.compose.ui.graphics.Color.Transparent
                                    },
                                ),
                        ) {
                            MessageRow(
                                message = message,
                                members = members,
                                displayMode = if (isServer) ChatDisplayMode.IRC_LINE else displayMode,
                                density = messageDensity,
                                showSeconds = showSeconds,
                                coloredNicklist = coloredNicklist,
                                showHostmaskInEvents = showHostmaskInEvents,
                                isMention = isMentionRow(message, myNick, isQuery, highlightPatterns),
                                isQuery = isQuery,
                                isMine = isQuery && (myNick ?: viewerUsername).equals(message.sender, ignoreCase = true),
                                onReply = viewModel::reply,
                                onLongPress = { nick, text ->
                                    longPressedMessageText = text
                                    viewModel.onMessageLongPress(nick)
                                },
                                tight = tight,
                            )
                        }
                    }
                }
                // A dedicated final row makes "go to bottom" unambiguous. Scrolling
                // to the last message alone can leave a long message clipped; this
                // anchor is always the final row and therefore clamps at the true
                // end of the viewport.
                item(key = "chat-bottom-anchor") {
                    Spacer(Modifier.size(1.dp))
                }
            }
            if (isLoadingOlder) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.chat_history_loading_older),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            error?.let { message ->
                ChatErrorSnackbar(
                    message = message,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
            // Pill con la data in cima mentre si scorre, stile Telegram: mostra il
            // giorno del primo messaggio visibile (Oggi / Ieri / 8 settembre).
            val topVisibleTime by remember {
                derivedStateOf {
                    if (messages.isEmpty()) {
                        null
                    } else {
                        val first = listState.firstVisibleItemIndex
                        val messageIndex = if (dividerIndex != null && first > dividerIndex) {
                            first - 1
                        } else {
                            first
                        }
                        messages.getOrNull(messageIndex.coerceIn(messages.indices))?.serverTime
                    }
                }
            }
            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = listState.isScrollInProgress && topVisibleTime != null,
                ) {
                    topVisibleTime?.let { DateChip(timeMillis = it) }
                }
                // Ephemeral server-query results, pinned above the scrollback like
                // cicchetto's inline cards — each dismissible, each replaced by the
                // next reply of its kind.
                val whowasValue = whowas
                if (whowasValue != null) {
                    Spacer(Modifier.size(8.dp))
                    WhowasCard(bundle = whowasValue, onDismiss = viewModel::dismissWhowas)
                }
                val lusersValue = lusers
                if (lusersValue != null) {
                    Spacer(Modifier.size(8.dp))
                    LusersCard(bundle = lusersValue, onDismiss = viewModel::dismissLusers)
                }
                val highlightNoticeValue = highlightNotice
                if (highlightNoticeValue != null) {
                    Spacer(Modifier.size(8.dp))
                    EphemeralResultCard(onDismiss = viewModel::dismissHighlightNotice, title = "/hilight") {
                        Text(
                            text = highlightNoticeValue,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Show the jump control only while there is content below the viewport.
            // The bottom anchor makes this check reliable even with long final rows.
            // When own-nick mentions sit below the fold the button becomes
            // mention-aware (cicchetto #360): a badge shows how many, and a tap
            // jumps to the nearest one below instead of the tail.
            if (showJumpToBottom) {
                val scope = rememberCoroutineScope()
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .zIndex(1f),
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            val target = nextMentionRowIndex
                            if (target != null) {
                                // Put the mention row at the top of the viewport;
                                // scrolling to the following row would hide the
                                // very mention this button is meant to reveal.
                                scope.launch {
                                    val maxRow = listState.layoutInfo.totalItemsCount - 1
                                    listState.animateScrollToItem(target.coerceAtMost(maxRow))
                                }
                            } else {
                                scope.launch { listState.animateToChatBottom() }
                            }
                        },
                    ) {
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = if (mentionBadgeCount > 0) {
                                pluralStringResource(
                                    R.plurals.cd_jump_to_next_mention,
                                    mentionBadgeCount,
                                    mentionBadgeCount,
                                )
                            } else {
                                stringResource(R.string.cd_scroll_to_bottom)
                            },
                        )
                    }
                    if (mentionBadgeCount > 0) {
                        MentionCountBadge(
                            count = mentionBadgeCount,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-6).dp),
                        )
                    }
                }
            }
                }
            }
        }
    }

    val whoisValue = whois
    if (whoisValue != null) {
        val ignored by viewModel.isIgnored(whoisValue.target).collectAsState(initial = false)
        val avatar by viewModel.avatarBitmap.collectAsState()
        UserCardSheet(
            whois = whoisValue,
            viewerUsername = viewerUsername,
            ownSigils = ownSigils,
            targetSigils = viewModel.sigilsFor(whoisValue.target),
            availableModes = privilegeModes,
            messageText = longPressedMessageText,
            onDismiss = {
                viewModel.dismissWhois()
                longPressedMessageText = null
            },
            onContactPrivately = viewModel::contactPrivately,
            onKick = viewModel::kickFromCard,
            onBan = viewModel::banFromCard,
            onSetMode = viewModel::setModeFromCard,
            showChannelActions = !isQuery,
            isIgnored = ignored,
            onIgnore = viewModel::ignore,
            onUnignore = viewModel::unignore,
            avatarBitmap = avatar,
        )
    }

    if (showTopicDialog && topic != null) {
        AlertDialog(
            onDismissRequest = { showTopicDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            title = {
                Text(
                    stringResource(R.string.chat_topic_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = { MircText(text = topic!!, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showTopicDialog = false }) {
                    Text(stringResource(R.string.chat_dialog_close))
                }
            },
        )
    }

    // `/who` roster modal — tap a nick to open a query, like cicchetto's WhoModal.
    // Nothing lands in the scrollback; the reply lives only in this modal.
    val whoReplyValue = whoReply
    if (whoReplyValue != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissWho,
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            title = {
                Text(
                    stringResource(R.string.chat_who_title, whoReplyValue.target),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                if (whoReplyValue.users.isEmpty()) {
                    Text(
                        stringResource(R.string.chat_who_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(whoReplyValue.users, key = { it.nick.lowercase() }) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.contactPrivately(user.nick)
                                        viewModel.dismissWho()
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.nick,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    val hostmask = "${user.user}@${user.host}".takeIf { user.user.isNotBlank() || user.host.isNotBlank() }
                                    hostmask?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    user.realname?.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissWho) {
                    Text(stringResource(R.string.chat_dialog_close))
                }
            },
        )
    }

    if (showCredits) {
        CreditsScreen(onClose = viewModel::dismissCredits)
    }

    // Flood guard: a multi-line draft would be sent as one PRIVMSG per line, so
    // a block taller than the threshold asks first. Cancel keeps the draft.
    val pendingSend = pendingMultiLineSend
    if (pendingSend != null) {
        val messageCount = MessageLines.splitMessageLines(pendingSend).size
        AlertDialog(
            onDismissRequest = viewModel::dismissMultiLineSend,
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            title = {
                Text(
                    stringResource(R.string.chat_multiline_title, messageCount),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    stringResource(R.string.chat_multiline_body, channelName, messageCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmMultiLineSend) {
                    Text(stringResource(R.string.cd_send))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissMultiLineSend) {
                    Text(stringResource(R.string.cd_cancel))
                }
            },
        )
    }
}

@Composable
private fun EphemeralResultCard(
    onDismiss: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.chat_dialog_close),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun EphemeralResultLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** `/whowas` result — same ephemeral inline card as cicchetto's WhowasCard,
 * including the "no history" surface for a 406 `not_found`. */
@Composable
private fun WhowasCard(bundle: WhowasBundleDto, onDismiss: () -> Unit) {
    EphemeralResultCard(onDismiss = onDismiss, title = bundle.target) {
        if (bundle.notFound) {
            EphemeralResultLine(stringResource(R.string.chat_whowas_not_found, bundle.target))
        } else {
            if (bundle.user != null || bundle.host != null) {
                EphemeralResultLine(
                    stringResource(
                        R.string.chat_whowas_user_line,
                        bundle.user.orEmpty(),
                        bundle.host.orEmpty(),
                    ),
                )
            }
            bundle.realname?.takeIf { it.isNotBlank() }?.let {
                EphemeralResultLine(stringResource(R.string.chat_whowas_realname_line, it))
            }
            bundle.server?.takeIf { it.isNotBlank() }?.let {
                EphemeralResultLine(stringResource(R.string.chat_whowas_server_line, it))
            }
            bundle.logoffTime?.takeIf { it.isNotBlank() }?.let {
                EphemeralResultLine(stringResource(R.string.chat_whowas_logoff_line, it))
            }
        }
    }
}

/** `/lusers` result — the RFC 2812 §3.4.2 counters, showing only the ones the
 * ircd actually sent (each is nullable), like cicchetto's LusersCard. */
@Composable
private fun LusersCard(bundle: LusersBundleDto, onDismiss: () -> Unit) {
    EphemeralResultCard(
        onDismiss = onDismiss,
        title = stringResource(R.string.chat_lusers_title, bundle.network),
    ) {
        bundle.totalUsers?.let { EphemeralResultLine(stringResource(R.string.chat_lusers_users_line, it)) }
        bundle.invisible?.let { EphemeralResultLine(stringResource(R.string.chat_lusers_invisible_line, it)) }
        bundle.servers?.let { EphemeralResultLine(stringResource(R.string.chat_lusers_servers_line, it)) }
        bundle.operators?.let { EphemeralResultLine(stringResource(R.string.chat_lusers_operators_line, it)) }
        bundle.unknownConnections?.let { EphemeralResultLine(stringResource(R.string.chat_lusers_unknown_line, it)) }
        bundle.channelsFormed?.let { EphemeralResultLine(stringResource(R.string.chat_lusers_channels_line, it)) }
        if (bundle.localClients != null || bundle.maxLocal != null) {
            EphemeralResultLine(
                stringResource(
                    R.string.chat_lusers_local_line,
                    bundle.localClients?.toString().orEmpty(),
                    bundle.maxLocal?.toString().orEmpty(),
                ),
            )
        }
        if (bundle.currentGlobal != null || bundle.maxGlobal != null) {
            EphemeralResultLine(
                stringResource(
                    R.string.chat_lusers_global_line,
                    bundle.currentGlobal?.toString().orEmpty(),
                    bundle.maxGlobal?.toString().orEmpty(),
                ),
            )
        }
    }
}

@Composable
internal fun SlashCommandSuggestions(
    suggestions: List<SlashCommandSpec>,
    onSelect: (SlashCommandSpec) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("slash-command-suggestions"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
            suggestions.forEach { command ->
                item(key = "slash-command-${command.name}") {
                    DropdownMenuItem(
                        modifier = Modifier.testTag("slash-command-${command.name}"),
                        text = {
                            Column {
                                Text("/${command.name}", fontWeight = FontWeight.Medium)
                                Text(
                                    stringResource(command.syntaxRes),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Text(
                                    stringResource(command.descriptionRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = { onSelect(command) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SlashArgumentSuggestions(
    suggestions: List<SlashArgumentSuggestion>,
    onSelect: (SlashArgumentSuggestion) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("slash-argument-suggestions"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
            suggestions.forEach { suggestion ->
                item(key = "slash-argument-${suggestion.value}") {
                    DropdownMenuItem(
                        modifier = Modifier.testTag("slash-argument-${suggestion.value}"),
                        text = { Text(suggestion.label) },
                        onClick = { onSelect(suggestion) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChatErrorSnackbar(message: String, modifier: Modifier = Modifier) {
    Snackbar(modifier = modifier.testTag("chat-error-snackbar")) {
        Text(message)
    }
}

/** Fast, local-only search over the rows already loaded into Room for this chat.
 * Keeping this pure makes the UI independent from the future server-side search API. */
internal fun findLocalChatMatches(messages: List<MessageEntity>, query: String): List<MessageEntity> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    return messages.filter { message ->
        message.sender.contains(needle, ignoreCase = true) ||
            message.body?.contains(needle, ignoreCase = true) == true
    }
}

private data class MentionQuery(
    val start: Int,
    val end: Int,
    val query: String,
)

private fun mentionQueryAtCursor(value: TextFieldValue): MentionQuery? {
    if (!value.selection.collapsed) return null
    val cursor = value.selection.end
    if (cursor !in 0..value.text.length) return null
    val atIndex = value.text.lastIndexOf('@', startIndex = cursor - 1)
    if (atIndex < 0) return null
    if (atIndex > 0 && !value.text[atIndex - 1].isWhitespace()) return null
    val query = value.text.substring(atIndex + 1, cursor)
    if (query.any(Char::isWhitespace)) return null
    return MentionQuery(start = atIndex, end = cursor, query = query)
}

private fun findMentionSuggestions(query: String, members: List<MemberEntity>): List<MemberEntity> =
    members
        .asSequence()
        .filter { query.isBlank() || it.nick.contains(query, ignoreCase = true) }
        .distinctBy { it.nick.lowercase() }
        .sortedWith(compareBy<MemberEntity>({ !it.nick.startsWith(query, ignoreCase = true) }, { it.nick.lowercase() }))
        .take(8)
        .toList()

@Composable
private fun DateChip(timeMillis: Long, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(timeMillis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val label = when (date) {
        today -> stringResource(R.string.chat_date_today)
        today.minusDays(1) -> stringResource(R.string.chat_date_yesterday)
        else -> {
            val pattern = if (date.year == today.year) "d MMMM" else "d MMMM yyyy"
            DateTimeFormatter.ofPattern(pattern, java.util.Locale.getDefault()).format(date)
        }
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = 4.dp,
        modifier = modifier.padding(top = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun UnreadDivider(density: MessageDensity) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = density.dividerVertical()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_unread_divider),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        )
    }
}

/** The nick's highest-priority role sigil (~&@%+), or "" if they hold none / aren't a
 * known member (e.g. a query partner, who never appears in a channel's member list). */
private fun nickPrefixFor(nick: String, members: List<MemberEntity>): String {
    val member = members.find { it.nick.equals(nick, ignoreCase = true) } ?: return ""
    return highestSigil(sigilsOf(member))?.toString().orEmpty()
}

/** A low-alpha tint of the theme's `primary` accent, laid OVER whatever background is
 * already there rather than replacing it — using the opaque container role
 * instead (the first attempt) paired badly with the existing text colors on a dark
 * theme (nick colors, mIRC colors, plain body text all assume a dark background; some
 * dynamic-color palettes resolve the container to a *light* tone even in dark
 * mode, which then read as low-contrast-to-illegible against them). A translucent wash
 * over the correct background can't produce that mismatch, on either theme. */
@Composable
private fun mentionHighlight(isMention: Boolean): Modifier =
    if (isMention) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) else Modifier

/** Corner badge on the jump-to-bottom FAB, mirroring the HomeScreen unread pill
 * (`UnreadBadge`). Shown only while own-nick mentions sit below the fold; its
 * count is the number still to reach, and a tap on the button jumps to the
 * nearest one instead of the tail. */
@Composable
private fun MentionCountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** Whether [message] is one that would have fired a notification — see
 * NotificationRouter.shouldNotify, which this deliberately mirrors (own messages never
 * count; a DM is skipped here rather than mirrored, since every message in an open DM
 * would "mention" you and highlighting all of them would just be noise, not a signal).
 * Once the `/hilight` watchlist has loaded, matching upgrades to cicchetto's
 * own-nick-UNION-patterns word-boundary match instead of the plain substring. */
private fun isMentionRow(
    message: MessageEntity,
    myNick: String?,
    isQuery: Boolean,
    highlightPatterns: List<String>?,
): Boolean {
    if (myNick == null || isQuery) return false
    if (message.sender.equals(myNick, ignoreCase = true)) return false
    return message.body?.let { body ->
        if (highlightPatterns == null) containsMention(body, myNick)
        else matchesHighlight(body, myNick, highlightPatterns)
    } ?: false
}

@Composable
private fun reasonSuffix(reason: String?): String =
    if (reason == null) "" else stringResource(R.string.paren_suffix, reason)

/** The sender nick prefixed with its `[ident@host]` mask when [showHostmask] is on and
 * the server actually captured one (join/part/quit only — see [FormattedEvent.System]'s
 * `userHost`, absent for kick/mode/nick_change). */
private fun actorLabel(sender: String, userHost: String?, showHostmask: Boolean): String =
    if (showHostmask && userHost != null) "$sender [$userHost]" else sender

/** Renders a structured [FormattedEvent.System] into its localized display line — the
 * templates themselves live in strings.xml so this varies by locale. */
@Composable
private fun systemEventText(event: FormattedEvent.System, showHostmask: Boolean): String = when (event) {
    is FormattedEvent.System.Join ->
        stringResource(R.string.event_join, actorLabel(event.sender, event.userHost, showHostmask))
    is FormattedEvent.System.Part ->
        stringResource(
            R.string.event_part,
            actorLabel(event.sender, event.userHost, showHostmask),
            reasonSuffix(event.reason),
        )
    is FormattedEvent.System.Quit ->
        stringResource(
            R.string.event_quit,
            actorLabel(event.sender, event.userHost, showHostmask),
            reasonSuffix(event.reason),
        )
    is FormattedEvent.System.Kick ->
        stringResource(R.string.event_kick, event.sender, event.target, reasonSuffix(event.reason))
    is FormattedEvent.System.Mode ->
        stringResource(R.string.event_mode, event.sender, event.modes, event.args?.let { " $it" }.orEmpty())
    is FormattedEvent.System.NickChange -> stringResource(R.string.event_nick_change, event.sender, event.newNick)
    is FormattedEvent.System.TopicChanged -> stringResource(R.string.event_topic_changed, event.sender)
}

@Composable
private fun MessageRow(
    message: MessageEntity,
    members: List<MemberEntity>,
    displayMode: ChatDisplayMode,
    density: MessageDensity,
    showSeconds: Boolean,
    coloredNicklist: Boolean,
    showHostmaskInEvents: Boolean,
    isMention: Boolean,
    isQuery: Boolean,
    isMine: Boolean,
    onReply: (nick: String, body: String) -> Unit,
    onLongPress: (nick: String, text: String) -> Unit,
    tight: Boolean = false,
) {
    val meta = remember(message.metaJson) {
        runCatching { AppJson.parseToJsonElement(message.metaJson).jsonObject }
            .getOrDefault(JsonObject(emptyMap()))
    }
    val formatted = remember(message.kind, message.sender, message.body, meta) {
        SystemEventFormatter.format(message.kind, message.sender, message.body, meta)
    }
    val time = remember(message.serverTime, showSeconds) { formatTime(message.serverTime, showSeconds) }
    val prefix = remember(message.sender, members) { nickPrefixFor(message.sender, members) }

    when (formatted) {
        is FormattedEvent.System -> {
            val eventText = systemEventText(formatted, showHostmaskInEvents)
            if (displayMode == ChatDisplayMode.IRC_LINE) {
                // Monoriga IRC anche per gli eventi: stessa riga compatta
                // monospace della conversazione, niente pill.
                MircText(
                    text = "[$time] -!- $eventText",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalResentinCodeFontFamily.current),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = density.lineVertical())
                        .pointerInput(formatted.sender, eventText) {
                            detectTapGestures(onLongPress = { onLongPress(formatted.sender, eventText) })
                        },
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = density.systemVertical()),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        ),
                        modifier = Modifier.pointerInput(formatted.sender, eventText) {
                            detectTapGestures(onLongPress = { onLongPress(formatted.sender, eventText) })
                        },
                    ) {
                        MircText(
                            text = "$eventText · $time",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
        is FormattedEvent.Chat -> {
            SwipeToReply(
                onReply = { onReply(message.sender, formatted.text) },
                onLongPress = { onLongPress(message.sender, formatted.text) },
            ) {
                if (displayMode == ChatDisplayMode.IRC_LINE) {
                    IrcLineRow(message, formatted, prefix, time, coloredNicklist, isMention, density)
                } else {
                    BubbleRow(
                        message, formatted, prefix, time, coloredNicklist, isMention,
                        isPrivate = isQuery,
                        isMine = isMine,
                        tight = tight,
                        density = density,
                    )
                }
            }
        }
    }
}

/** Nick color (when [coloredNicklist] is on) applies to the bare nick only — the role
 * prefix sigil (`@`/`+`/...) and any "* "/"< >"/"(notice)" decoration around it stay
 * the surrounding text's own color, matching how real IRC clients color-code nicks. */
private fun buildNickLine(
    before: String,
    prefix: String,
    sender: String,
    after: String,
    body: String,
    coloredNicklist: Boolean,
    lightTheme: Boolean = false,
) = buildAnnotatedString {
    append(before)
    append(prefix)
    if (coloredNicklist) {
        withStyle(SpanStyle(color = colorForNick(sender, lightTheme))) { append(sender) }
    } else {
        append(sender)
    }
    append(after)
    append(withClickableLinks(mircAnnotatedString(body, lightTheme), linkStylesFor(lightTheme)))
}

@Composable
private fun BubbleRow(
    message: MessageEntity,
    formatted: FormattedEvent.Chat,
    prefix: String,
    time: String,
    coloredNicklist: Boolean,
    isMention: Boolean,
    isPrivate: Boolean,
    isMine: Boolean,
    tight: Boolean = false,
    density: MessageDensity = MessageDensity.NORMAL,
) {
    val isOwnPrivate = isPrivate && isMine
    val bubbleColor = when {
        isMention -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        isOwnPrivate -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val bubbleBorder = when {
        isMention -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
        isOwnPrivate -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = density.rowVertical(tight)),
        // Query messages use the same left-aligned conversation flow as IRC chat.
        // The sender is still differentiated by the bubble tint below, not by
        // switching sides of the conversation.
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (isMention) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, end = 6.dp)
                    .size(width = 2.dp, height = 40.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(20.dp),
            color = bubbleColor,
            border = bubbleBorder,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (formatted.isAction) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        val lightTheme = isLightTheme()
                        val annotated = remember(prefix, message.sender, formatted.text, coloredNicklist, lightTheme) {
                            buildNickLine("* ", prefix, message.sender, " ", formatted.text, coloredNicklist, lightTheme)
                        }
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (formatted.isNotice) {
                                prefix + message.sender + " (notice)"
                            } else {
                                prefix + message.sender
                            },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (coloredNicklist) colorForNick(message.sender, isLightTheme()) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    MircText(text = formatted.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** `[HH:mm] <nick> message`, classic IRC-client style. Role prefix (if any) sits right
 * inside the angle brackets — `<@nick>` — matching how real IRC clients render it. */
@Composable
private fun IrcLineRow(
    message: MessageEntity,
    formatted: FormattedEvent.Chat,
    prefix: String,
    time: String,
    coloredNicklist: Boolean,
    isMention: Boolean,
    density: MessageDensity = MessageDensity.NORMAL,
) {
    val lightTheme = isLightTheme()
    val annotated = remember(message.sender, formatted.text, formatted.isAction, formatted.isNotice, prefix, time, coloredNicklist, lightTheme) {
        when {
            formatted.isAction -> buildNickLine("[$time] * ", prefix, message.sender, " ", formatted.text, coloredNicklist, lightTheme)
            formatted.isNotice -> buildNickLine("[$time] -", prefix, message.sender, "- ", formatted.text, coloredNicklist, lightTheme)
            else -> buildNickLine("[$time] <", prefix, message.sender, "> ", formatted.text, coloredNicklist, lightTheme)
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalResentinCodeFontFamily.current),
        modifier = Modifier
            .fillMaxWidth()
            .then(mentionHighlight(isMention))
            .padding(horizontal = 16.dp, vertical = density.lineVertical()),
    )
}

/** Vertical rhythm of chat rows — NORMAL preserves the previous spacing. */
private fun MessageDensity.rowVertical(tight: Boolean): Dp = when (this) {
    MessageDensity.COMPACT -> if (tight) 1.dp else 4.dp
    MessageDensity.NORMAL -> if (tight) 2.dp else 8.dp
    MessageDensity.COMFORTABLE -> if (tight) 4.dp else 12.dp
}

private fun MessageDensity.lineVertical(): Dp = when (this) {
    MessageDensity.COMPACT -> 1.dp
    MessageDensity.NORMAL -> 2.dp
    MessageDensity.COMFORTABLE -> 4.dp
}

private fun MessageDensity.systemVertical(): Dp = when (this) {
    MessageDensity.COMPACT -> 2.dp
    MessageDensity.NORMAL -> 4.dp
    MessageDensity.COMFORTABLE -> 6.dp
}

private fun MessageDensity.dividerVertical(): Dp = when (this) {
    MessageDensity.COMPACT -> 4.dp
    MessageDensity.NORMAL -> 8.dp
    MessageDensity.COMFORTABLE -> 12.dp
}

private const val REPLY_SWIPE_THRESHOLD_DP = 64

/** Swipe-right-to-reply, WhatsApp/Telegram style: drag reveals a reply icon behind the
 * row and, past the threshold, prefills the draft with `nick: ` on release. IRC has no
 * real threaded replies, so this only ever affects the compose box, never the message.
 * A separate long-press gesture opens the sender's user card — the drag detector only
 * consumes events once the finger has actually moved, so the two coexist on one row. */
@Composable
private fun SwipeToReply(onReply: () -> Unit, onLongPress: () -> Unit, content: @Composable () -> Unit) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val thresholdPx = with(LocalDensity.current) { REPLY_SWIPE_THRESHOLD_DP.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetX.value > thresholdPx) onReply()
                            offsetX.animateTo(0f, animationSpec = spring())
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f, animationSpec = spring()) } },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo((offsetX.value + dragAmount).coerceIn(0f, thresholdPx * 1.5f)) }
                    },
                )
            }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) },
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = stringResource(R.string.cd_reply),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
                .graphicsLayer { alpha = (offsetX.value / thresholdPx).coerceIn(0f, 1f) },
        )
        Box(modifier = Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }) {
            content()
        }
    }
}
