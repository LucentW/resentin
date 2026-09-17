package pm.antani.resentin.ui.chat

import pm.antani.resentin.ui.theme.ResentinSpacing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.unit.sp
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
import pm.antani.resentin.net.dto.UPLOAD_TTL_LADDER_SECONDS
import pm.antani.resentin.net.dto.WhoReplyDto
import pm.antani.resentin.net.dto.WhowasBundleDto
import pm.antani.resentin.irc.canonicalTarget
import pm.antani.resentin.irc.highestSigil
import pm.antani.resentin.net.AppJson
import pm.antani.resentin.domain.repository.PendingDccOffer
import pm.antani.resentin.ui.common.LocalDccFileDownloadHandler
import pm.antani.resentin.ui.common.LocalIrcChannelLinkHandler
import pm.antani.resentin.ui.common.LocalStripMircFormatting
import pm.antani.resentin.ui.common.MircText
import pm.antani.resentin.ui.common.stripMircCodes
import pm.antani.resentin.ui.common.rememberAvatarBitmap
import pm.antani.resentin.ui.common.ResentinDropdown
import pm.antani.resentin.ui.common.ResentinDropdownMenu
import pm.antani.resentin.ui.common.ResentinDropdownMenuItem
import pm.antani.resentin.ui.common.ResentinDropdownOption
import pm.antani.resentin.ui.common.ResentinFilterChip
import pm.antani.resentin.ui.common.ResentinHeaderAction
import pm.antani.resentin.ui.common.ResentinEmptyState
import pm.antani.resentin.ui.common.ResentinErrorState
import pm.antani.resentin.ui.common.ResentinStateBanner
import pm.antani.resentin.ui.common.ResentinStateTone
import pm.antani.resentin.ui.common.ResentinLoadingState
import pm.antani.resentin.ui.common.UserCardSheet
import pm.antani.resentin.ui.common.colorForNick
import pm.antani.resentin.ui.common.isLightTheme
import pm.antani.resentin.ui.common.linkStylesFor
import pm.antani.resentin.ui.common.mircAnnotatedString
import pm.antani.resentin.ui.common.sigilsOf
import pm.antani.resentin.ui.common.withClickableLinks
import pm.antani.resentin.ui.theme.LocalResentinChatFontFamily
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


@Composable
private fun ReplyComposerBar(
    reply: PendingReply,
    coloredNicklist: Boolean,
    onCancel: () -> Unit,
) {
    val nickColor = if (coloredNicklist) colorForNick(reply.nick, isLightTheme()) else MaterialTheme.colorScheme.primary
    val preview = remember(reply.messageBody) { buildReplyPreview(reply.messageBody) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                    .clip(MaterialTheme.shapes.small)
                .background(nickColor),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chat_replying_to_prefix),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = reply.nick,
                    style = MaterialTheme.typography.labelLarge,
                    color = nickColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
                Text(
                text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.cd_cancel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

@Composable
private fun AttachmentPreviewCard(
    attachment: PendingUploadConfirm,
    isUploading: Boolean,
    onRemove: () -> Unit,
) {
    val removeAttachmentLabel = stringResource(R.string.chat_attachment_remove)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
        ),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AttachFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(attachment.mimeType, formatFileSizeOrUnknown(attachment.sizeBytes))
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onRemove,
                enabled = !isUploading,
                modifier = Modifier
                    .size(48.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = removeAttachmentLabel
                    },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatFileSizeOrUnknown(bytes: Long): String =
    if (bytes >= 0L) formatFileSize(bytes) else "—"

@Composable
private fun AnimatedReplyComposerBar(
    reply: PendingReply,
    coloredNicklist: Boolean,
    onCancel: () -> Unit,
) {
    AnimatedContent(
        targetState = reply,
        transitionSpec = {
            (fadeIn(animationSpec = tween(160)) + slideInVertically(
                initialOffsetY = { height -> height / 4 },
                animationSpec = tween(200),
            )) togetherWith
                (fadeOut(animationSpec = tween(110)) + slideOutVertically(
                    targetOffsetY = { height -> -height / 4 },
                    animationSpec = tween(160),
                ))
        },
        label = "reply_composer_change",
    ) { animatedReply ->
        ReplyComposerBar(
            reply = animatedReply,
            coloredNicklist = coloredNicklist,
            onCancel = onCancel,
        )
    }
}
private fun formatTime(epochMillis: Long, showSeconds: Boolean): String {
    val formatter = if (showSeconds) TIME_FORMATTER_WITH_SECONDS else TIME_FORMATTER
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    // Epoch-millis jump target from the mentions pseudo-window (0 = none).
    // Resolved against loaded rows by serverTime; pages older history while
    // the target stays above what's cached.
    jumpToServerTime: Long = 0L,
) {
    val messages by viewModel.messages.collectAsState()
    val allMessages by viewModel.allMessages.collectAsState()
    val topic by viewModel.topic.collectAsState()
    val channelModes by viewModel.channelModes.collectAsState()
    val topicSubtitle = remember(channelModes, topic) {
        buildString {
            if (channelModes != null) append("($channelModes) ")
            if (topic != null) append(topic)
        }.takeIf { it.isNotBlank() }
    }
    val peerAvatarUrl by viewModel.peerAvatarUrl.collectAsState()
    val peerAvatarBitmap = rememberAvatarBitmap(peerAvatarUrl, viewModel::fetchAvatarBytes)
    val draft by viewModel.draft.collectAsState()
    val pendingReply by viewModel.pendingReply.collectAsState()
    // Local TextFieldValue (not just the String from the ViewModel) so an externally
    // triggered draft change — a swipe-to-reply prefill, or send() clearing it — can
    // explicitly place the cursor, instead of leaning on Compose's default "diff the new
    // text against whatever selection happened to be there" behavior, which does NOT
    // reliably land the cursor at the end (the reported bug: reply prefilled the text but
    // the caret stayed wherever it last was, not focused, not at the end).
    var draftFieldValue by remember { mutableStateOf(TextFieldValue(draft)) }
    var composerToolsOpen by remember { mutableStateOf(false) }
    var recentMentionNicks by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedSuggestionIndex by remember { mutableStateOf(0) }
    val draftFocusRequester = remember { FocusRequester() }
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(draft) {
        if (draftFieldValue.text != draft) {
            draftFieldValue = TextFieldValue(text = draft, selection = TextRange(draft.length))
        }
    }
    LaunchedEffect(Unit) {
        viewModel.replyFocusRequests.collect { draftFocusRequester.requestFocus() }
    }
    // Screen-off-then-on (or any backgrounding) while this exact chat stays open never
    // recreates the ViewModel, so its one-shot init{} backfill gets no second chance to
    // run — a connection that silently died while asleep left the chat frozen until the
    // user noticed and manually pulled to refresh. ON_RESUME re-triggers the same
    // backfill silently on every actual resume (including the harmless common case
    // where nothing was missed) — see ChatViewModel.onResumed.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val error by viewModel.error.collectAsState()
    val pendingMultiLineSend by viewModel.pendingMultiLineSend.collectAsState()
    val pendingUpload by viewModel.pendingUpload.collectAsState()
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
    val mentionSuggestions = remember(activeMention, members, recentMentionNicks) {
        activeMention?.let { findMentionSuggestions(it.query, members, recentMentionNicks) }.orEmpty()
    }
    val slashSuggestions = remember(draftFieldValue.text) {
        suggestSlashCommands(draftFieldValue.text)
    }
    val availableChannels by viewModel.availableChannels.collectAsState()
    val availableNetworks by viewModel.availableNetworks.collectAsState()
    val slashArgumentSuggestions = remember(draftFieldValue.text, members, availableChannels, availableNetworks, recentMentionNicks) {
        suggestSlashArguments(
            draftFieldValue.text,
            members.map { it.nick },
            availableChannels,
            availableNetworks,
            recentNicks = recentMentionNicks,
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
        recentMentionNicks = listOf(nick) + recentMentionNicks
            .filterNot { it.equals(nick, ignoreCase = true) }
            .take(15)
        selectedSuggestionIndex = 0
        draftFocusRequester.requestFocus()
    }

    fun completeSlashCommand(command: SlashCommandSpec) {
        val completion = completeSlashCommandInput(draftFieldValue.text, command)
        val newValue = TextFieldValue(completion.text, TextRange(completion.cursor))
        draftFieldValue = newValue
        viewModel.onDraftChange(completion.text)
        selectedSuggestionIndex = 0
        draftFocusRequester.requestFocus()
    }
    fun completeSlashArgument(suggestion: SlashArgumentSuggestion) {
        val completion = completeSlashArgumentInput(draftFieldValue.text, suggestion)
        val newValue = TextFieldValue(completion.text, TextRange(completion.cursor))
        draftFieldValue = newValue
        viewModel.onDraftChange(completion.text)
        if (members.any { it.nick.equals(suggestion.value, ignoreCase = true) }) {
            recentMentionNicks = listOf(suggestion.value) + recentMentionNicks
                .filterNot { it.equals(suggestion.value, ignoreCase = true) }
                .take(15)
        }
        selectedSuggestionIndex = 0
        draftFocusRequester.requestFocus()
    }
    val activeSuggestionCount = when {
        mentionSuggestions.isNotEmpty() -> mentionSuggestions.size
        slashArgumentSuggestions.isNotEmpty() -> slashArgumentSuggestions.size
        slashSuggestions.isNotEmpty() -> slashSuggestions.size
        else -> 0
    }
    LaunchedEffect(activeMention?.query, draftFieldValue.text, activeSuggestionCount) {
        selectedSuggestionIndex = 0
    }
    fun moveSuggestionSelection(step: Int) {
        selectedSuggestionIndex = cycleSuggestionIndex(selectedSuggestionIndex, step, activeSuggestionCount)
    }
    fun selectActiveSuggestion(): Boolean {
        when {
            mentionSuggestions.isNotEmpty() -> mentionSuggestions.getOrNull(selectedSuggestionIndex)?.let { completeMention(it.nick) }
            slashArgumentSuggestions.isNotEmpty() -> slashArgumentSuggestions.getOrNull(selectedSuggestionIndex)?.let { completeSlashArgument(it) }
            slashSuggestions.isNotEmpty() -> slashSuggestions.getOrNull(selectedSuggestionIndex)?.let { completeSlashCommand(it) }
            else -> return false
        }
        return true
    }
    val openReferencedChannel = remember(channelName, viewModel) {
        { referencedChannel: String ->
            if (canonicalTarget(referencedChannel) != canonicalTarget(channelName)) {
                viewModel.requestOpenChannelFromReference(referencedChannel)
            }
        }
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
    val smartPresenceFilter by viewModel.smartPresenceFilter.collectAsState()
    val myNick by viewModel.myNick.collectAsState()
    val awayState by viewModel.awayState.collectAsState()
    val peerAwayMessage by viewModel.peerAwayMessage.collectAsState()
    val highlightPatterns by viewModel.highlightPatterns.collectAsState()
    val whowas by viewModel.whowas.collectAsState()
    val whoReply by viewModel.whoReply.collectAsState()
    val lusers by viewModel.lusers.collectAsState()
    val links by viewModel.links.collectAsState()
    val umodeModalSlug by viewModel.umodeModal.collectAsState()
    val umodeNetworkId by viewModel.umodeNetworkId.collectAsState()
    val umodesByNetworkId by viewModel.umodesByNetworkId.collectAsState()
    val supportedUmodesByNetworkId by viewModel.supportedUmodesByNetworkId.collectAsState()
    val serverReply by viewModel.serverReply.collectAsState()
    val recover by viewModel.recover.collectAsState()
    val highlightNotice by viewModel.highlightNotice.collectAsState()
    val pendingDccOffers by viewModel.pendingDccOffers.collectAsState()
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
    var topicExpanded by remember(networkSlug, channelName) { mutableStateOf(false) }
    var channelJoinConfirmation by remember(networkSlug, channelName) { mutableStateOf<String?>(null) }
    var showChannelMenu by remember { mutableStateOf(false) }
    var expandedPresenceBursts by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showActivitySheet by remember { mutableStateOf(false) }
    var activityFilter by remember { mutableStateOf(ActivityFilter.ALL) }
    var revealAllPresenceEvents by remember { mutableStateOf(false) }
    var pendingActivityJumpId by remember { mutableStateOf<Long?>(null) }
    // The long-pressed row's plain text, threaded to UserCardSheet for its "Copia"/
    // "Copia parziale" actions — the sheet itself only knows the sender's nick (it opens
    // off a WHOIS reply, which arrives async), not which message triggered it.
    var longPressedMessageText by remember { mutableStateOf<String?>(null) }
    // Long-press message menu target (Copy / Reply / !addquote / Select… / user
    // card — cicchetto MessageContextMenu parity). Null = menu closed.
    var messageMenuTarget by remember { mutableStateOf<MessageMenuTarget?>(null) }
    // Text-selection latch: the one message id whose body renders inside a
    // SelectionContainer (cicchetto's "Select…" escape hatch). Gestures on that
    // row are disabled while latched; back clears it.
    var selectingMessageId by remember { mutableStateOf<Long?>(null) }
    if (selectingMessageId != null) {
        BackHandler { selectingMessageId = null }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::uploadFile)
    }

    if (composerToolsOpen) {
        BackHandler { composerToolsOpen = false }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToQuery.collect { nick -> onOpenQuery(networkSlug, nick) }
    }

    LaunchedEffect(Unit) {
        viewModel.commandEffects.collect { effect ->
            when (effect) {
                is ChatCommandEffect.OpenChannel -> onOpenChannel(networkSlug, effect.channelName)
                is ChatCommandEffect.ConfirmChannelJoin -> channelJoinConfirmation = effect.channelName
                ChatCommandEffect.CloseChat -> onBack()
                ChatCommandEffect.OpenChannelSettings -> onSettingsClick()
                ChatCommandEffect.OpenAppSettings -> onAppSettings()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.scrollToLatestAfterSend.collect {
            listState.animateToChatBottom()
        }
    }

    // Position (within the displayed timeline) of the first row past the read cursor —
    // also where the "Hai letto fino a qui" divider renders. During the first layout
    // use the frozen cursor so it remains a stable landing target. Once positioned,
    // switch to the live cursor so the divider disappears as soon as this chat is
    // confirmed read, without requiring a navigation away and back.
    // Null when there's nothing to mark (cursor still loading, never read anything, or
    // everything is already read).
    val dividerCursor = if (positioned) readCursor ?: initialReadCursor else initialReadCursor
    val smartFilterActive = smartPresenceFilter && !searchOpen && !revealAllPresenceEvents && !isQuery && !isServer
    val timelineMessages = if (revealAllPresenceEvents) allMessages else messages
    val timelineRows = remember(timelineMessages, dividerCursor, smartFilterActive, myNick) {
        buildChatTimeline(
            timelineMessages,
            dividerCursor,
            smartPresenceFilterEnabled = smartFilterActive,
            alwaysVisibleSender = myNick,
        )
    }
    val dividerIndex = dividerCursor?.let { cursor ->
        timelineRows.indexOfFirst { row -> row.messages.any { it.id > cursor } }.takeIf { it >= 0 }
    }

    // List indices of the mention rows (own nick / /hilight match from another
    // sender — the SAME isMentionRow predicate the per-row highlight uses, the
    // way cicchetto's badge reads the rows the highlight marks). The unread
    // divider shifts every displayed row after it by one. Precomputed so the
    // scroll-path decision stays a cheap filter.
    val mentionRowIndices by remember(timelineRows, dividerIndex, myNick, highlightPatterns, isQuery) {
        derivedStateOf {
            val divider = dividerIndex
            val indices = mutableListOf<Int>()
            timelineRows.forEachIndexed { index, row ->
                if (row.messages.any { isMentionRow(it, myNick, isQuery, highlightPatterns) }) {
                    indices.add(index + if (divider != null && index >= divider) 1 else 0)
                }
            }
            indices
        }
    }

    LaunchedEffect(searchOpen, selectedSearchMessageId, timelineRows, dividerIndex) {
        if (!searchOpen || selectedSearchMessageId == null) return@LaunchedEffect
        val rowIndex = timelineRows.indexOfFirst { row -> row.messages.any { it.id == selectedSearchMessageId } }
        if (rowIndex < 0) return@LaunchedEffect
        val listIndex = rowIndex + if (dividerIndex != null && rowIndex >= dividerIndex) 1 else 0
        listState.animateScrollToItem(listIndex)
    }

    LaunchedEffect(pendingActivityJumpId, timelineRows, dividerIndex) {
        val targetId = pendingActivityJumpId ?: return@LaunchedEffect
        val rowIndex = timelineRows.indexOfFirst { row -> row.messages.any { it.id == targetId } }
        if (rowIndex < 0) return@LaunchedEffect
        val row = timelineRows[rowIndex]
        if (row is ChatTimelineRow.PresenceSummary) {
            expandedPresenceBursts = expandedPresenceBursts + row.messages.first().id
        }
        val listIndex = rowIndex + if (dividerIndex != null && rowIndex >= dividerIndex) 1 else 0
        listState.animateScrollToItem(listIndex)
        pendingActivityJumpId = null
    }

    // Mentions-window jump (C8.2): land on the first loaded row at or after the
    // tapped row's serverTime. The effect re-runs as history loads; while the
    // target stays older than everything cached, page further back (loadOlder
    // is self-guarded). Consumed once jumped or proven unreachable (history
    // exhausted above the target — e.g. pruned rows).
    var mentionJumpConsumed by remember(jumpToServerTime) { mutableStateOf(false) }
    LaunchedEffect(jumpToServerTime, messages, initialHistoryReady) {
        if (jumpToServerTime <= 0L || mentionJumpConsumed || messages.isEmpty()) return@LaunchedEffect
        val hit = messages.filter { it.serverTime >= jumpToServerTime }.minByOrNull { it.serverTime }
        if (hit != null) {
            pendingActivityJumpId = hit.id
            mentionJumpConsumed = true
        } else if (initialHistoryReady) {
            val oldest = messages.minOf { it.serverTime }
            if (oldest > jumpToServerTime) viewModel.loadOlder()
            else mentionJumpConsumed = true
        }
    }

    // Land on the first unread message (per the server's read-cursor), not always the
    // bottom — only once, and only once we actually know where that is (see
    // ChatViewModel.initialReadCursorReady: null is ambiguous between "not loaded yet"
    // and "never read anything").
    LaunchedEffect(timelineRows, initialReadCursorReady) {
        if (initialListIndex != null || !initialReadCursorReady || messages.isEmpty()) return@LaunchedEffect
        initialListIndex = dividerIndex ?: (timelineRows.size - 1)
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
    var shouldScrollToBottomOnComposerTools by remember { mutableStateOf(false) }
    // Track multiline growth too: IME insets stay constant while the composer gets taller.
    var composerHeightPx by remember { mutableStateOf(0) }
    var composerBarHeightPx by remember { mutableStateOf(0) }
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(listState, composerHeightPx, composerBarHeightPx) {
        snapshotFlow {
            val bottomIndex = timelineRows.size + if (dividerIndex != null) 1 else 0
            Triple(
                imeInsets.getBottom(density),
                Pair(
                    shouldScrollToBottomOnIme && positioned,
                    shouldScrollToBottomOnComposerTools && composerToolsOpen && positioned,
                ),
                bottomIndex,
            )
        }.collectLatest { (imeBottom, follows, bottomIndex) ->
            val shouldFollowIme = follows.first
            val shouldFollowTools = follows.second
            if ((!shouldFollowIme && !shouldFollowTools) || (imeBottom <= 0 && !shouldFollowTools) || bottomIndex < 0) {
                return@collectLatest
            }
            // IME insets animate over multiple frames. Keep the tail aligned
            // after each inset change, once the current layout has measured.
            withFrameNanos { }
            listState.scrollToItem(bottomIndex)
            if (shouldFollowTools) shouldScrollToBottomOnComposerTools = false
        }
    }

    // Only auto-follow to the tail when the reader was already there — landing on the
    // unread divider (potentially far above the bottom, e.g. after a big backfill) must
    // NOT get yanked down the instant one more message arrives; a still-unread backlog
    // stays exactly where the reader put it. loadOlder() prepends at the head, which
    // doesn't change `lastOrNull()?.id`, so this never fires for that case either.
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (positioned && messages.isNotEmpty() && isAtBottom) {
            val bottomIndex = timelineRows.size + if (dividerIndex != null) 1 else 0
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

    CompositionLocalProvider(
        LocalIrcChannelLinkHandler provides openReferencedChannel,
    ) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
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
                            shape = MaterialTheme.shapes.medium,
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
                                    modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.extraSmall),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Column {
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
                        val subtitle = topicSubtitle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
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
                                    shape = MaterialTheme.shapes.extraSmall,
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
                                if (topic != null) {
                                    val topicActionLabel = stringResource(
                                        if (topicExpanded) R.string.cd_collapse_topic else R.string.cd_expand_topic,
                                    )
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(MaterialTheme.shapes.extraSmall)
                                            .clickable { topicExpanded = !topicExpanded }
                                            .semantics(mergeDescendants = true) {
                                                role = Role.Button
                                                contentDescription = topicActionLabel
                                            },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        MircText(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Icon(
                                            imageVector = if (topicExpanded) {
                                                Icons.Outlined.KeyboardArrowUp
                                            } else {
                                                Icons.Outlined.KeyboardArrowDown
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                } else {
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
                            stateDescription = stringResource(
                                if (showChannelMenu) R.string.cd_menu_open else R.string.cd_menu_closed,
                            ),
                        )
                        ResentinDropdownMenu(
                            expanded = showChannelMenu,
                            onDismissRequest = { showChannelMenu = false },
                        ) {
                            if (!isServer) {
                                ResentinDropdownMenuItem(
                                    text = stringResource(R.string.chat_activity_action, allMessages.count { it.kind in SYSTEM_EVENT_KINDS }),
                                    icon = Icons.Outlined.History,
                                    onClick = {
                                        showChannelMenu = false
                                        showActivitySheet = true
                                    },
                                )
                            }
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
            if (topicExpanded && topic != null && !searchOpen) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            MircText(
                                text = topic.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(
                            onClick = { topicExpanded = false },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Outlined.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.cd_collapse_topic),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (isQuery && peerAwayMessage != null && !searchOpen) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_peer_away_message, channelName, peerAwayMessage.orEmpty()),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = viewModel::dismissPeerAway, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cd_cancel),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    thickness = 1.dp,
                )
                if (slashSuggestions.isNotEmpty()) {
                    SlashCommandSuggestions(
                        suggestions = slashSuggestions,
                        selectedIndex = selectedSuggestionIndex,
                        onSelect = ::completeSlashCommand,
                    )
                }
                if (slashArgumentSuggestions.isNotEmpty()) {
                    SlashArgumentSuggestions(
                        suggestions = slashArgumentSuggestions,
                        selectedIndex = selectedSuggestionIndex,
                        onSelect = ::completeSlashArgument,
                    )
                }
                if (mentionSuggestions.isNotEmpty()) {
                    MentionSuggestions(
                        suggestions = mentionSuggestions,
                        selectedIndex = selectedSuggestionIndex,
                        onSelect = { completeMention(it.nick) },
                    )
                }
                val draftEmpty = draftFieldValue.text.isEmpty()
                val canSendDraft = draftFieldValue.text.isNotBlank()
                val canSendAttachment = pendingUpload?.requiresConfirmation == false
                val hasSendableText = canSendDraft || canSendAttachment
                    val sendScale by animateFloatAsState(
                        targetValue = when {
                            isSending -> 0.9f
                            hasSendableText -> 1.08f
                            else -> 1f
                        },
                        animationSpec = spring(),
                        label = "send_button_scale",
                    )
                    val sendRotation by animateFloatAsState(
                        targetValue = if (isSending) 6f else 0f,
                        animationSpec = tween(180),
                        label = "send_button_rotation",
                    )
                val slashCommandsLabel = stringResource(R.string.cd_slash_commands)
                val attachFileLabel = stringResource(R.string.cd_attach_file)
                val composerToolsLabel = stringResource(if (composerToolsOpen) R.string.composer_tools_close else R.string.composer_tools_open)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                            .onSizeChanged { composerBarHeightPx = it.height },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    ),
                    tonalElevation = 0.dp,
                ) {
                    Column {
                        AnimatedVisibility(
                            visible = pendingReply != null,
                            enter = expandVertically(animationSpec = tween(220)) +
                                fadeIn(animationSpec = tween(170)) +
                                slideInVertically(
                                    initialOffsetY = { height -> -height / 4 },
                                    animationSpec = tween(220),
                                ),
                            exit = shrinkVertically(animationSpec = tween(180)) +
                                fadeOut(animationSpec = tween(120)) +
                                slideOutVertically(
                                    targetOffsetY = { height -> -height / 4 },
                                    animationSpec = tween(180),
                                ),
                        ) {
                            pendingReply?.let { reply ->
                                AnimatedReplyComposerBar(
                                    reply = reply,
                                    coloredNicklist = coloredNicklist,
                                    onCancel = viewModel::cancelReply,
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = pendingUpload != null,
                            enter = expandVertically(animationSpec = tween(200)) +
                                fadeIn(animationSpec = tween(160)),
                            exit = shrinkVertically(animationSpec = tween(160)) +
                                fadeOut(animationSpec = tween(110)),
                        ) {
                            pendingUpload?.let { attachment ->
                                AttachmentPreviewCard(
                                    attachment = attachment,
                                    isUploading = isUploading,
                                    onRemove = viewModel::dismissPendingUpload,
                                )
                            }
                        }
                    ComposerTools(
                        visible = composerToolsOpen,
                        isUploading = isUploading,
                        onAttachFile = { filePicker.launch("*/*") },
                        value = draftFieldValue,
                        onValueChange = { newValue ->
                            draftFieldValue = newValue
                            viewModel.onDraftChange(newValue.text)
                        },
                        onFocusComposer = { draftFocusRequester.requestFocus() },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextField(
                            value = draftFieldValue,
                            onValueChange = { newValue ->
                                draftFieldValue = newValue
                                viewModel.onDraftChange(newValue.text)
                            },
                            leadingIcon = {
                                IconButton(
                                    onClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val opening = !composerToolsOpen
                                        shouldScrollToBottomOnComposerTools = opening && positioned && isAtBottom
                                        composerToolsOpen = opening
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            if (composerToolsOpen) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainer,
                                            CircleShape,
                                        )
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = composerToolsLabel
                                        },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null,
                                        modifier = Modifier.graphicsLayer {
                                            rotationZ = if (composerToolsOpen) 45f else 0f
                                        },
                                        tint = if (composerToolsOpen) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(max = 120.dp)
                                .onSizeChanged { composerHeightPx = it.height }
                                .focusRequester(draftFocusRequester)
                                .onFocusChanged { focusState ->
                                    shouldScrollToBottomOnIme = if (focusState.isFocused) {
                                        positioned && isAtBottom
                                    } else {
                                        false
                                    }
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown || activeSuggestionCount == 0) {
                                        false
                                    } else {
                                        when (event.key) {
                                            Key.DirectionDown -> { moveSuggestionSelection(1); true }
                                            Key.DirectionUp -> { moveSuggestionSelection(-1); true }
                                            Key.Enter, Key.Tab -> if (event.key == Key.Enter && event.isShiftPressed) false else selectActiveSuggestion()
                                            else -> false
                                        }
                                    }
                                },
                            placeholder = {
                                Text(
                                    stringResource(R.string.chat_message_placeholder),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = LocalResentinChatFontFamily.current,
                                    ),
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = LocalResentinChatFontFamily.current,
                            ),
                            visualTransformation = MircComposerVisualTransformation,
                            minLines = 1,
                            maxLines = 4,
                            shape = RoundedCornerShape(28.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        )
                        AnimatedVisibility(
                            visible = draftEmpty,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            IconButton(
                                onClick = {
                                    val commandStarter = TextFieldValue("/", TextRange(1))
                                    draftFieldValue = commandStarter
                                    viewModel.onDraftChange(commandStarter.text)
                                    draftFocusRequester.requestFocus()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainer,
                                        CircleShape,
                                    )
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = slashCommandsLabel
                                    },
                            ) {
                                Text(
                                    "/",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = draftEmpty && !composerToolsOpen,
                            enter = fadeIn(animationSpec = tween(150)),
                            exit = fadeOut(animationSpec = tween(120)),
                        ) {
                            IconButton(
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    filePicker.launch("*/*")
                                },
                                enabled = !isUploading,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainer,
                                        CircleShape,
                                    )
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = attachFileLabel
                                    },
                            ) {
                                if (isUploading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        Icons.Outlined.AttachFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.send()
                            },
                            enabled = !isSending && !isUploading && hasSendableText,
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = if (hasSendableText) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                    shape = CircleShape,
                                )
                                .graphicsLayer {
                                    scaleX = sendScale
                                    scaleY = sendScale
                                    rotationZ = sendRotation
                                },
                        ) {
                            AnimatedContent(
                                targetState = isSending,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(120)) togetherWith
                                        fadeOut(animationSpec = tween(90))
                                },
                                label = "send_button_content",
                            ) { sending ->
                                if (sending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.Send,
                                        contentDescription = stringResource(R.string.cd_send),
                                        tint = if (hasSendableText) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
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
                    ResentinErrorState(
                        icon = Icons.Outlined.WifiOff,
                        title = stringResource(R.string.chat_history_error_title),
                        description = stringResource(R.string.chat_history_error_description),
                        actionLabel = stringResource(R.string.chat_history_retry),
                        onRetry = viewModel::refresh,
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
                timelineRows.forEachIndexed { index, row ->
                    if (index == dividerIndex) {
                        item(key = "unread-divider") { UnreadDivider(density = messageDensity) }
                    }
                    when (row) {
                        is ChatTimelineRow.Message -> item(key = row.key) {
                            val message = row.message
                            val previous = timelineRows.getOrNull(index - 1)?.messages?.lastOrNull()
                            // Persistent in-list day chip (motd parity): drawn inside this
                            // row's own item, so item indexes, keys and jump math never shift.
                            val showDay = previous == null || !isSameDay(previous.serverTime, message.serverTime)
                            val tight = index != dividerIndex &&
                                continuesMessageGroup(previous, message)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (showDay) DaySeparatorRow(timeMillis = message.serverTime, density = messageDensity)
                            ChatTimelineMessageItem(
                                message = message,
                                members = members,
                                displayMode = if (isServer) ChatDisplayMode.IRC_LINE else displayMode,
                                density = messageDensity,
                                showSeconds = showSeconds,
                                coloredNicklist = coloredNicklist,
                                showHostmaskInEvents = showHostmaskInEvents,
                                isMention = isMentionRow(message, myNick, isQuery, highlightPatterns),
                                isQuery = isQuery,
                                isMine = (myNick ?: viewerUsername).equals(message.sender, ignoreCase = true),
                                isSelected = message.id == selectedSearchMessageId,
                                onReply = viewModel::reply,
                                onMessageMenu = { target -> messageMenuTarget = target },
                                selectingMessageId = selectingMessageId,
                                tight = tight,
                            )
                            }
                        }
                        is ChatTimelineRow.PresenceSummary -> item(key = row.key) {
                            val burstKey = row.messages.first().id
                            val selectedInBurst = row.messages.any { it.id == selectedSearchMessageId }
                            val expanded = selectedInBurst || burstKey in expandedPresenceBursts
                            val uniqueUsers = row.messages.map { it.sender.lowercase() }.distinct().size
                            val senderLabel = row.sender ?: pluralStringResource(
                                R.plurals.chat_activity_users,
                                uniqueUsers,
                                uniqueUsers,
                            )
                            // Same day chip as message rows: the burst is one visual unit,
                            // so the chip goes above it, never between its events.
                            val burstPrevious = timelineRows.getOrNull(index - 1)?.messages?.lastOrNull()
                            val burstFirst = row.messages.first()
                            val showBurstDay = burstPrevious == null ||
                                !isSameDay(burstPrevious.serverTime, burstFirst.serverTime)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (showBurstDay) DaySeparatorRow(timeMillis = burstFirst.serverTime, density = messageDensity)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                PresenceBurstSummaryRow(
                                    sender = senderLabel,
                                    joins = row.joins,
                                    leaves = row.leaves,
                                    time = formatTime(row.messages.last().serverTime, showSeconds),
                                    isIrcLine = isServer || displayMode == ChatDisplayMode.IRC_LINE,
                                    expanded = expanded,
                                    selected = selectedInBurst,
                                    onToggle = {
                                        expandedPresenceBursts = if (burstKey in expandedPresenceBursts) {
                                            expandedPresenceBursts - burstKey
                                        } else {
                                            expandedPresenceBursts + burstKey
                                        }
                                    },
                                )
                                if (expanded) {
                                    row.messages.forEach { event ->
                                        ChatTimelineMessageItem(
                                            message = event,
                                            members = members,
                                            displayMode = if (isServer) ChatDisplayMode.IRC_LINE else displayMode,
                                            density = messageDensity,
                                            showSeconds = showSeconds,
                                            coloredNicklist = coloredNicklist,
                                            showHostmaskInEvents = showHostmaskInEvents,
                                            isMention = false,
                                            isQuery = isQuery,
                                            isMine = (myNick ?: viewerUsername).equals(event.sender, ignoreCase = true),
                                            isSelected = event.id == selectedSearchMessageId,
                                            onReply = viewModel::reply,
                                            onMessageMenu = { target -> messageMenuTarget = target },
                                            selectingMessageId = selectingMessageId,
                                            tight = false,
                                        )
                                    }
                                }
                            }
                            }
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
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = ResentinSpacing.large, vertical = ResentinSpacing.small),
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
                    if (timelineRows.isEmpty()) {
                        null
                    } else {
                        val first = listState.firstVisibleItemIndex
                        val rowIndex = if (dividerIndex != null && first > dividerIndex) first - 1 else first
                        timelineRows.getOrNull(rowIndex.coerceIn(timelineRows.indices))?.messages?.firstOrNull()?.serverTime
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
                pendingDccOffers.forEach { offer ->
                    Spacer(Modifier.size(8.dp))
                    DccOfferCard(
                        offer = offer,
                        onAccept = { viewModel.acceptDccOffer(offer) },
                        onDecline = { viewModel.declineDccOffer(offer) },
                    )
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

    channelJoinConfirmation?.let { target ->
        AlertDialog(
            onDismissRequest = { channelJoinConfirmation = null },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            title = {
                Text(
                    stringResource(R.string.chat_channel_link_join_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    stringResource(R.string.chat_channel_link_join_body, target),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        channelJoinConfirmation = null
                        viewModel.confirmChannelJoinFromReference(target)
                    },
                ) {
                    Text(stringResource(R.string.home_new_chat_join))
                }
            },
            dismissButton = {
                TextButton(onClick = { channelJoinConfirmation = null }) {
                    Text(stringResource(R.string.cd_cancel))
                }
            },
        )
    }

    if (showActivitySheet) {
        val activityMessages = remember(allMessages, activityFilter) {
            allMessages.asReversed().filter { message ->
                message.kind in SYSTEM_EVENT_KINDS && when (activityFilter) {
                    ActivityFilter.ALL -> true
                    ActivityFilter.PRESENCE -> message.kind in PRESENCE_EVENT_KINDS
                    ActivityFilter.OTHER -> message.kind !in PRESENCE_EVENT_KINDS
                }
            }
        }
        ModalBottomSheet(
            onDismissRequest = { showActivitySheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .padding(horizontal = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.chat_activity_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showActivitySheet = false }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.chat_dialog_close))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResentinFilterChip(
                        selected = activityFilter == ActivityFilter.ALL,
                        onClick = { activityFilter = ActivityFilter.ALL },
                        label = { Text(stringResource(R.string.chat_activity_filter_all)) },
                    )
                    ResentinFilterChip(
                        selected = activityFilter == ActivityFilter.PRESENCE,
                        onClick = { activityFilter = ActivityFilter.PRESENCE },
                        label = { Text(stringResource(R.string.chat_activity_filter_presence)) },
                    )
                    ResentinFilterChip(
                        selected = activityFilter == ActivityFilter.OTHER,
                        onClick = { activityFilter = ActivityFilter.OTHER },
                        label = { Text(stringResource(R.string.chat_activity_filter_other)) },
                    )
                }
                Spacer(Modifier.size(8.dp))
                if (activityMessages.isEmpty()) {
                    Text(
                        stringResource(R.string.chat_activity_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(activityMessages, key = { it.id }) { event ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showActivitySheet = false
                                        revealAllPresenceEvents = true
                                        pendingActivityJumpId = event.id
                                    },
                            ) {
                                Text(
                                    formatTime(event.serverTime, showSeconds),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                                )
                                ChatTimelineMessageItem(
                                    message = event,
                                    members = members,
                                    displayMode = ChatDisplayMode.IRC_LINE,
                                    density = messageDensity,
                                    showSeconds = showSeconds,
                                    coloredNicklist = coloredNicklist,
                                    showHostmaskInEvents = showHostmaskInEvents,
                                    isMention = false,
                                    isQuery = isQuery,
                                    isMine = (myNick ?: viewerUsername).equals(event.sender, ignoreCase = true),
                                    isSelected = false,
                                    onReply = viewModel::reply,
                                    onMessageMenu = { target -> messageMenuTarget = target },
                                    selectingMessageId = selectingMessageId,
                                    tight = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    messageMenuTarget?.let { target ->
        MessageActionsSheet(
            target = target,
            onDismiss = { messageMenuTarget = null },
            onReply = {
                viewModel.reply(target.sender, target.text)
                messageMenuTarget = null
            },
            onAddQuote = {
                viewModel.appendAddQuote(target.sender, target.text, target.isAction)
                messageMenuTarget = null
            },
            onSelect = {
                selectingMessageId = target.messageId
                messageMenuTarget = null
            },
            onUserCard = {
                longPressedMessageText = target.text
                viewModel.onMessageLongPress(target.sender)
                messageMenuTarget = null
            },
        )
    }

    val whoisValue = whois
    if (whoisValue != null) {        val ignored by viewModel.isIgnored(whoisValue.target).collectAsState(initial = false)
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
            onContactPrivately = { nick ->
                // Dismiss FIRST, synchronously: navigating (pop+push) while the
                // sheet dialog is still open leaves the transition stuck on the
                // old screen. The emit->collect->navigate chain is async, so the
                // sheet is always gone from composition before navigate runs
                // (same ordering as the /who modal below, which dismisses right
                // after contactPrivately for the same reason).
                viewModel.dismissWhois()
                longPressedMessageText = null
                viewModel.contactPrivately(nick)
            },
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

    // `/who` roster modal — tap a nick to open a query, like cicchetto's WhoModal.
    // Nothing lands in the scrollback; the reply lives only in this modal.
    // Flags decoded per the bahamut grammar with roster-authoritative membership
    // (#272) and per-network PREFIX rank (issue 1999); realname via MircText.
    val whoReplyValue = whoReply
    if (whoReplyValue != null) {
        val whoRank = remember(privilegeModes) { sigilRankFor(privilegeModes) }
        AlertDialog(
            onDismissRequest = viewModel::dismissWho,
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            title = {
                Text(
                    stringResource(
                        R.string.chat_who_title_count,
                        whoReplyValue.target,
                        whoReplyValue.users.size,
                    ),
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
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(whoReplyValue.users, key = { it.nick.lowercase() }) { user ->
                                val resolved = remember(user, members, whoRank, privilegeModes) {
                                    resolveWhoRow(
                                        user.modes,
                                        whoRosterFor(user, whoReplyValue.target, channelName, members, whoRank),
                                        whoRank,
                                    )
                                }
                                val chips = remember(resolved, privilegeModes) {
                                    whoChips(resolved, privilegeModes)
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.contactPrivately(user.nick)
                                            viewModel.dismissWho()
                                        }
                                        .padding(vertical = 8.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = (resolved.membership ?: "") + user.nick,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(top = 4.dp),
                                    ) {
                                        chips.forEach { chip ->
                                            Surface(
                                                shape = MaterialTheme.shapes.small,
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            ) {
                                                Text(
                                                    text = chip.label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                    user.realname?.takeIf { it.isNotBlank() }?.let {
                                        MircText(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            enableLinks = false,
                                        )
                                    }
                                    val hostmask =
                                        "${user.user}@${user.host}".takeIf { user.user.isNotBlank() || user.host.isNotBlank() }
                                    hostmask?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    val server = user.server.takeIf { it.isNotBlank() }
                                    Row {
                                        server?.let {
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        user.hops?.let {
                                            Text(
                                                text = stringResource(R.string.chat_who_hops, it),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(start = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.chat_who_footer, whoReplyValue.users.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
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

    // `/links` topology snapshot — lista nativa (cicchetto renders the same
    // bundle as an interactive SVG map; same data, same empty states, native
    // list surface). Ephemeral, last-write-wins, dismiss drops it.
    val linksValue = links
    if (linksValue != null) {
        val sortedLinks = remember(linksValue) {
            linksValue.entries.sortedWith(
                compareBy({ it.hopcount ?: Int.MAX_VALUE }, { it.server.lowercase() }),
            )
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissLinks,
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            title = {
                Text(
                    stringResource(R.string.chat_links_title, linksValue.entries.size),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                if (linksValue.entries.isEmpty()) {
                    Column {
                        if (linksValue.mask != null) {
                            Text(
                                text = stringResource(R.string.chat_links_empty_nomatch, checkNotNull(linksValue.mask)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.chat_links_empty_nomatch_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.chat_links_empty_restricted),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.chat_links_empty_restricted_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(sortedLinks, key = { it.server.lowercase() }) { entry ->
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = entry.server,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        entry.hopcount?.let {
                                            Text(
                                                text = stringResource(R.string.chat_links_hops, it),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(start = 8.dp),
                                            )
                                        }
                                    }
                                    val isRoot = entry.linkedTo != null && entry.linkedTo.equals(entry.server, ignoreCase = true)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isRoot) {
                                            Text(
                                                text = stringResource(R.string.chat_links_you),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        } else {
                                            entry.linkedTo?.takeIf { it.isNotBlank() }?.let {
                                                Text(
                                                    text = stringResource(R.string.chat_links_uplink, it),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                    entry.description?.takeIf { it.isNotBlank() }?.let {
                                        MircText(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            enableLinks = false,
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.chat_links_footer, linksValue.entries.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissLinks) {
                    Text(stringResource(R.string.chat_dialog_close))
                }
            },
        )
    }

    // Umode viewer/editor — cicchetto UmodeModal parity: own umodes as toggle
    // buttons, server/services-managed ones read-only. Same `umode` verb as
    // `/umode +x` (one feature, one code path).
    val umodeSlug = umodeModalSlug
    if (umodeSlug != null) {
        val umodeId = umodeNetworkId
        val activeUmodes = remember(umodesByNetworkId, umodeId) {
            umodeId?.let { umodesByNetworkId[it] } ?: emptyList()
        }
        val serverSet = remember(supportedUmodesByNetworkId, umodeId) {
            umodeId?.let { supportedUmodesByNetworkId[it] } ?: emptyList()
        }
        val toggles = remember(activeUmodes, serverSet) { availableUmodes(activeUmodes, serverSet) }
        AlertDialog(
            onDismissRequest = viewModel::dismissUmodeModal,
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            title = {
                Text(
                    stringResource(R.string.chat_umode_title, umodeSlug),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(toggles, key = { it.letter }) { mode ->
                        val active = mode.letter in activeUmodes
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (active) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            onClick = { viewModel.toggleUmode(mode, active) },
                            enabled = mode.settable,
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "+${mode.letter}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (!mode.settable) {
                                        Text(
                                            text = stringResource(R.string.chat_umode_server_set),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Text(
                                    text = mode.desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissUmodeModal) {
                    Text(stringResource(R.string.chat_dialog_close))
                }
            },
        )
    }

    // Server-text reply (`/info`/`/version`/`/motd`/`/admin`) — cicchetto
    // ServerReplyModal parity: monospace verbatim line list via MircText,
    // title per source, count footer. Ephemeral, last-write-wins.
    val serverReplyValue = serverReply
    if (serverReplyValue != null) {
        val replyTitle = when (serverReplyValue.source) {
            "info" -> stringResource(R.string.chat_server_reply_info)
            "version" -> stringResource(R.string.chat_server_reply_version)
            "motd" -> stringResource(R.string.chat_server_reply_motd)
            "admin" -> stringResource(R.string.chat_server_reply_admin)
            else -> serverReplyValue.source
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissServerReply,
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = replyTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                if (serverReplyValue.lines.isEmpty()) {
                    Text(
                        text = stringResource(R.string.chat_server_reply_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(serverReplyValue.lines) { line ->
                                MircText(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    enableLinks = false,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                )
                            }
                        }
                        Text(
                            text = if (serverReplyValue.lines.size == 1) {
                                stringResource(R.string.chat_server_reply_footer_one)
                            } else {
                                stringResource(R.string.chat_server_reply_footer, serverReplyValue.lines.size)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissServerReply) {
                    Text(stringResource(R.string.chat_dialog_close))
                }
            },
        )
    }

    // "Recover my identity" progress — cicchetto RecoverModal parity:
    // server-driven steps with running/ok/failed marks, terminal success or
    // reason-localized failure. No retry (re-issue /recover). Shown only for
    // this chat's network; another network's flight never mixes in.
    val recoverValue = recover?.takeIf { it.networkSlug.equals(networkSlug, ignoreCase = true) }
    if (recoverValue != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRecover,
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = stringResource(R.string.chat_recover_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.chat_recover_copy, recoverValue.networkSlug),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    recoverValue.steps.forEach { step ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            Text(
                                text = when (step.status) {
                                    "ok" -> "✓"
                                    "failed" -> "✗"
                                    else -> "…"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = when (step.status) {
                                    "ok" -> MaterialTheme.colorScheme.primary
                                    "failed" -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.width(24.dp),
                            )
                            Text(
                                text = when (step.step) {
                                    "identify" -> stringResource(R.string.chat_recover_step_identify)
                                    "register" -> stringResource(R.string.chat_recover_step_register)
                                    "nick" -> stringResource(R.string.chat_recover_step_nick)
                                    "recover" -> stringResource(R.string.chat_recover_step_recover)
                                    "release" -> stringResource(R.string.chat_recover_step_release)
                                    else -> step.step
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    when (recoverValue.outcome) {
                        "succeeded" -> {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.chat_recover_success, recoverValue.networkSlug),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        "failed" -> {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = when (recoverValue.reason) {
                                    "wrong_password" -> stringResource(R.string.chat_recover_failure_wrong_password)
                                    "nick_unavailable" -> stringResource(R.string.chat_recover_failure_nick_unavailable)
                                    "services_declined" -> stringResource(R.string.chat_recover_failure_services_declined)
                                    else -> stringResource(R.string.chat_recover_failure_generic)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissRecover) {
                    Text(
                        stringResource(
                            if (recoverValue.outcome == null) R.string.chat_recover_close
                            else R.string.chat_recover_done,
                        ),
                    )
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
            shape = MaterialTheme.shapes.large,
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


    // #1883 — pre-upload confirm (opt-in, server-side). The staged file goes out
    // only on Send, with the TTL picked here (a per-batch choice, not saved).
    val stagedUpload = pendingUpload
    if (stagedUpload?.requiresConfirmation == true && !isUploading) {
        UploadConfirmDialog(
            pending = stagedUpload,
            channelName = channelName,
            onTtlChange = viewModel::onPendingUploadTtlChange,
            onConfirm = viewModel::confirmPendingUpload,
            onDismiss = viewModel::dismissPendingUpload,
        )
    }
    }
}

/** #1883 — pre-upload confirm dialog (opt-in, server-side). Shows what is about
 * to leave the device (name, size, type, destination) plus the per-batch TTL
 * choice; Send uploads with that TTL, Cancel drops the staged file. A one-off
 * stays a one-off: the choice is never written back to the stored preference. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UploadConfirmDialog(
    pending: PendingUploadConfirm,
    channelName: String,
    onTtlChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        title = {
            Text(
                stringResource(R.string.chat_upload_confirm_title, channelName),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    pending.fileName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val sizeLabel = if (pending.sizeBytes >= 0) formatFileSize(pending.sizeBytes) else null
                Text(
                    listOfNotNull(pending.mimeType, sizeLabel).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.chat_upload_confirm_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.chat_upload_ttl_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                ResentinDropdown(
                    selected = pending.ttlSeconds,
                    options = UPLOAD_TTL_LADDER_SECONDS.map { seconds ->
                        ResentinDropdownOption(seconds, uploadTtlDropdownLabel(seconds))
                    },
                    onSelected = onTtlChange,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.cd_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cd_cancel))
            }
        },
    )
}

@Composable
private fun uploadTtlDropdownLabel(seconds: Int): String = when (seconds) {
    3600 -> stringResource(R.string.chat_upload_ttl_1h)
    43200 -> stringResource(R.string.chat_upload_ttl_12h)
    86400 -> stringResource(R.string.chat_upload_ttl_24h)
    259200 -> stringResource(R.string.chat_upload_ttl_72h)
    else -> stringResource(R.string.chat_upload_ttl_custom, seconds)
}

/** Consent banner for a held DCC offer (issue 2089 on grappa-irc) — the close (×)
 * button IS the decline action, same as every other [EphemeralResultCard] dismissal;
 * there is a separate, explicit Accept button because unlike those cards this one has
 * a real consequence (a transfer actually starts). */
@Composable
private fun DccOfferCard(offer: PendingDccOffer, onAccept: () -> Unit, onDecline: () -> Unit) {
    EphemeralResultCard(onDismiss = onDecline, title = stringResource(R.string.chat_dcc_offer_title)) {
        Text(
            text = stringResource(R.string.chat_dcc_offer_body, offer.from, offer.filename, formatFileSize(offer.size)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onAccept) {
                Text(stringResource(R.string.chat_dcc_offer_accept), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** `1234` -> `"1,2 KB"`, whole MB/GB units the way a human reads a size, not a raw
 * byte count — the peer's own claim ([PendingDccOffer.size]), never a measured value. */
private fun formatFileSize(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) "$bytes ${units[0]}" else "%.1f %s".format(value, units[unitIndex])
}

@Composable
private fun EphemeralResultCard(
    onDismiss: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
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
internal fun MentionSuggestions(
    suggestions: List<MemberEntity>,
    selectedIndex: Int = -1,
    onSelect: (MemberEntity) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("mention-suggestions"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 208.dp).padding(vertical = 4.dp)) {
            itemsIndexed(suggestions, key = { _, member -> "mention-${member.nick}" }) { index, member ->
                DropdownMenuItem(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (index == selectedIndex) MaterialTheme.colorScheme.primaryContainer
                            else androidx.compose.ui.graphics.Color.Transparent,
                        )
                        .testTag("mention-${member.nick}"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    text = { Text(member.nick, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onSelect(member) },
                )
            }
        }
    }
}
@Composable
internal fun SlashCommandSuggestions(
    suggestions: List<SlashCommandSpec>,
    selectedIndex: Int = -1,
    onSelect: (SlashCommandSpec) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("slash-command-suggestions"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 208.dp).padding(vertical = 4.dp)) {
            itemsIndexed(suggestions, key = { _, command -> "slash-command-${command.name}" }) { index, command ->
                DropdownMenuItem(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (index == selectedIndex) MaterialTheme.colorScheme.primaryContainer
                            else androidx.compose.ui.graphics.Color.Transparent,
                        )
                        .testTag("slash-command-${command.name}"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        text = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "/${command.name} - ${stringResource(command.syntaxRes)}",
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(command.descriptionRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        onClick = { onSelect(command) },
                    )
                }
            }
        }
    }

@Composable
internal fun SlashArgumentSuggestions(
    suggestions: List<SlashArgumentSuggestion>,
    selectedIndex: Int = -1,
    onSelect: (SlashArgumentSuggestion) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("slash-argument-suggestions"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 208.dp).padding(vertical = 4.dp)) {
            itemsIndexed(suggestions, key = { _, suggestion -> "slash-argument-${suggestion.value}" }) { index, suggestion ->
                DropdownMenuItem(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (index == selectedIndex) MaterialTheme.colorScheme.primaryContainer
                            else androidx.compose.ui.graphics.Color.Transparent,
                        )
                        .testTag("slash-argument-${suggestion.value}"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    text = { Text(suggestion.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onSelect(suggestion) },
                )
            }
        }
    }
}

@Composable
internal fun ChatErrorSnackbar(message: String, modifier: Modifier = Modifier) {
    ResentinStateBanner(
        icon = Icons.Outlined.WifiOff,
        title = stringResource(R.string.ui_error_title),
        description = message,
        tone = ResentinStateTone.ERROR,
        modifier = modifier.testTag("chat-error-snackbar"),
    )
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

private fun findMentionSuggestions(query: String, members: List<MemberEntity>, recentNicks: List<String>): List<MemberEntity> =
    members
        .asSequence()
        .filter { query.isBlank() || it.nick.contains(query, ignoreCase = true) }
        .distinctBy { it.nick.lowercase() }
        .sortedWith(compareBy<MemberEntity>({ recentSuggestionRank(it.nick, recentNicks) }, { !it.nick.startsWith(query, ignoreCase = true) }, { it.nick.lowercase() }))
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
        shape = MaterialTheme.shapes.extraSmall,
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

/** Persistent in-list day chip (motd parity): same look as the floating
 * DateChip shown while scrolling, but always visible. Drawn inside the
 * opening row's own LazyColumn item (never a separate item), so item
 * indexes, keys and jump math never shift. Works in both display modes. */
@Composable
private fun DaySeparatorRow(timeMillis: Long, density: MessageDensity) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = density.dividerVertical()),
        contentAlignment = Alignment.Center,
    ) {
        DateChip(timeMillis = timeMillis)
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
            shape = MaterialTheme.shapes.extraSmall,
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
                shape = MaterialTheme.shapes.extraSmall,
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
private fun ChatTimelineMessageItem(
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
    isSelected: Boolean,
    onReply: (nick: String, body: String) -> Unit,
    onMessageMenu: (MessageMenuTarget) -> Unit,
    selectingMessageId: Long?,
    tight: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                else androidx.compose.ui.graphics.Color.Transparent,
            ),
    ) {
        MessageRow(
            message = message,
            members = members,
            displayMode = displayMode,
            density = density,
            showSeconds = showSeconds,
            coloredNicklist = coloredNicklist,
            showHostmaskInEvents = showHostmaskInEvents,
            isMention = isMention,
            isQuery = isQuery,
            isMine = isMine,
            onReply = onReply,
            onMessageMenu = onMessageMenu,
            selectingMessageId = selectingMessageId,
            tight = tight,
        )
    }
}

@Composable
private fun PresenceBurstSummaryRow(
    sender: String,
    joins: Int,
    leaves: Int,
    time: String,
    isIrcLine: Boolean,
    expanded: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val joinsLabel = pluralStringResource(R.plurals.chat_presence_joins, joins, joins)
    val leavesLabel = pluralStringResource(R.plurals.chat_presence_leaves, leaves, leaves)
    val summary = stringResource(R.string.chat_presence_burst_summary, sender, joinsLabel, leavesLabel)
    val actionLabel = stringResource(
        if (expanded) R.string.chat_presence_burst_collapse else R.string.chat_presence_burst_expand,
    )

    if (isIrcLine) {
        MircText(
            text = "[$time] -!- $summary · $actionLabel",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalResentinCodeFontFamily.current),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ResentinSpacing.xxLarge, vertical = ResentinSpacing.small),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                else MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.clickable(onClick = onToggle),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MircText(
                        text = "$summary · $time",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalResentinChatFontFamily.current),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = 300.dp),
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = actionLabel,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp).size(18.dp),
                    )
                }
            }
        }
    }
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
    onMessageMenu: (MessageMenuTarget) -> Unit,
    selectingMessageId: Long?,
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
                            detectTapGestures(
                                onLongPress = {
                                    onMessageMenu(
                                        MessageMenuTarget(message.id, formatted.sender, eventText, isChat = false),
                                    )
                                },
                            )
                        },
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ResentinSpacing.xxLarge, vertical = density.systemVertical()),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        ),
                        modifier = Modifier.pointerInput(formatted.sender, eventText) {
                            detectTapGestures(
                                onLongPress = {
                                    onMessageMenu(
                                        MessageMenuTarget(message.id, formatted.sender, eventText, isChat = false),
                                    )
                                },
                            )
                        },
                    ) {
                        MircText(
                            text = "$eventText · $time",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = LocalResentinChatFontFamily.current),
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
                onLongPress = {
                    onMessageMenu(
                        MessageMenuTarget(
                            message.id,
                            message.sender,
                            formatted.text,
                            isChat = true,
                            isAction = formatted.isAction,
                        ),
                    )
                },
                // Text selection owns the row's gestures while latched — the
                // drag/long-press detectors would fight SelectionContainer.
                gesturesEnabled = selectingMessageId == null,
            ) {
                val selecting = selectingMessageId == message.id
                if (displayMode == ChatDisplayMode.IRC_LINE) {
                    IrcLineRow(message, formatted, prefix, time, coloredNicklist, isMention, density, selecting)
                } else {
                    BubbleRow(
                        message, formatted, prefix, time, coloredNicklist, isMention,
                        isMine = isMine,
                        tight = tight,
                        density = density,
                        selecting = selecting,
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
    onDccFileClick: (path: String, filename: String?) -> Unit = { _, _ -> },
    stripFormatting: Boolean = false,
    onChannelClick: ((channelName: String) -> Unit)? = null,
) = buildAnnotatedString {
    append(before)
    append(prefix)
    if (coloredNicklist) {
        withStyle(SpanStyle(color = colorForNick(sender, lightTheme))) { append(sender) }
    } else {
        append(sender)
    }
    append(after)
    append(withClickableLinks(mircAnnotatedString(body, lightTheme, stripFormatting), linkStylesFor(lightTheme), onDccFileClick, onChannelClick))
}

// MESSAGE_GROUP_WINDOW_MS lives in ChatTimeline.kt next to continuesMessageGroup.
private val SYSTEM_EVENT_KINDS = setOf("join", "part", "quit", "kick", "mode", "nick_change", "topic")
private val PRESENCE_EVENT_KINDS = setOf("join", "part", "quit")
private enum class ActivityFilter { ALL, PRESENCE, OTHER }

// Reply-quote block above a bubble body (cicchetto scrollback-reply-quote
// parity): thin bar + quoted nick/message, laid out the same way the bubble's
// own header does it — nick (colored, same rule coloredNicklist applies
// everywhere else) on its own line, the quoted text dimmed underneath.
@Composable
private fun QuoteHeadBlock(head: String, barColor: androidx.compose.ui.graphics.Color, coloredNicklist: Boolean, lightTheme: Boolean) {
    // Quote heads render as plain text (never mIRC-styled), so stripping here only
    // removes the raw control codes that would otherwise survive as-is.
    val strip = LocalStripMircFormatting.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val parsed = remember(head, strip) {
        val parts = quoteHeadParts(head)
        if (parts == null) {
            null
        } else {
            val nick = if (strip) stripMircCodes(parts.nick) else parts.nick
            val preview = (if (strip) stripMircCodes(parts.preview) else parts.preview).trimEnd()
            Triple(if (parts.isAction) "* $nick" else nick, preview, parts.isAction)
        }
    }
    Row(
        modifier = Modifier.padding(bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp, end = 6.dp)
                .size(width = 2.dp, height = 28.dp)
                .background(barColor, CircleShape),
        )
        if (parsed == null) {
            Text(
                text = (if (strip) stripMircCodes(head) else head).trimEnd(),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalResentinChatFontFamily.current),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        } else {
            val (nickLabel, preview, isAction) = parsed
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = nickLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = LocalResentinChatFontFamily.current),
                    color = if (coloredNicklist) colorForNick(nickLabel.removePrefix("* "), lightTheme) else primaryColor,
                    fontWeight = FontWeight.SemiBold,
                    fontStyle = if (isAction) FontStyle.Italic else FontStyle.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalResentinChatFontFamily.current),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BubbleRow(
    message: MessageEntity,
    formatted: FormattedEvent.Chat,
    prefix: String,
    time: String,
    coloredNicklist: Boolean,
    isMention: Boolean,
    isMine: Boolean,
    tight: Boolean = false,
    density: MessageDensity = MessageDensity.NORMAL,
    selecting: Boolean = false,
) {
    // WhatsApp-style: i messaggi propri stanno a destra, gli altri a sinistra.
    // isMention non scatta mai per i propri (vedi isMentionRow), ma resta primo
    // per sicurezza.
    val isOutgoing = isMine
    val continuesGroup = tight && !isMention
    val bubbleColor = when {
        isMention -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        isOutgoing -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val bubbleBorder = when {
        isMention -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
        isOutgoing -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
    val lightTheme = isLightTheme()
    val dccFileHandler = LocalDccFileDownloadHandler.current
    val stripFormatting = LocalStripMircFormatting.current
    val channelClickHandler = LocalIrcChannelLinkHandler.current
    val timestampStyle = SpanStyle(
        fontSize = 11.sp,
        fontStyle = FontStyle.Normal,
        fontFamily = LocalResentinChatFontFamily.current,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Reply quote head (`<nick> … << `, cicchetto's own QUOTE template) renders
    // as a dimmed block above the body (scrollback-reply-quote parity) — actions
    // keep their third-person grammar untouched.
    val (quoteHead, quoteRest) = remember(formatted.text, formatted.isAction) {
        if (formatted.isAction) null to formatted.text else splitQuoteHead(formatted.text)
    }
    val bodyWithTime = remember(quoteRest, formatted.isNotice, continuesGroup, time, lightTheme, timestampStyle, dccFileHandler, stripFormatting, channelClickHandler) {
        buildAnnotatedString {
            if (formatted.isNotice && continuesGroup) {
                withStyle(timestampStyle) { append("(notice) ") }
            }
            append(withClickableLinks(mircAnnotatedString(quoteRest, lightTheme, stripFormatting), linkStylesFor(lightTheme), dccFileHandler, channelClickHandler))
            if (continuesGroup) {
                append("  ")
                withStyle(timestampStyle) { append(time) }
            }
        }
    }
    val actionWithTime = remember(
        prefix, message.sender, formatted.text, coloredNicklist, lightTheme, continuesGroup, time, timestampStyle, dccFileHandler, stripFormatting, channelClickHandler,
    ) {
        buildAnnotatedString {
            if (continuesGroup) {
                append("* ")
            } else {
                append(
                    buildNickLine(
                        before = "* ",
                        prefix = prefix,
                        sender = message.sender,
                        after = " ",
                        body = "",
                        coloredNicklist = coloredNicklist,
                        lightTheme = lightTheme,
                        onDccFileClick = dccFileHandler,
                        stripFormatting = stripFormatting,
                        onChannelClick = channelClickHandler,
                    ),
                )
                withStyle(timestampStyle) { append(time) }
                append(" ")
            }
            append(withClickableLinks(mircAnnotatedString(formatted.text, lightTheme, stripFormatting), linkStylesFor(lightTheme), dccFileHandler, channelClickHandler))
            if (continuesGroup) {
                append("  ")
                withStyle(timestampStyle) { append(time) }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * 0.88f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = density.rowVertical(continuesGroup)),
            // Stile WhatsApp/Telegram: outgoing a destra, incoming a sinistra.
            // La larghezza segue comunque il contenuto fino al cap.
            horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
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
                modifier = Modifier.widthIn(max = maxBubbleWidth),
                shape = if (continuesGroup) {
                    if (isOutgoing) {
                        RoundedCornerShape(topStart = 20.dp, topEnd = 7.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
                    } else {
                        RoundedCornerShape(topStart = 7.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
                    }
                } else {
                    MaterialTheme.shapes.medium
                },
                color = bubbleColor,
                border = bubbleBorder,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    if (!continuesGroup && !formatted.isAction && !isOutgoing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (formatted.isNotice) {
                                    "$prefix${message.sender} (notice)"
                                } else {
                                    prefix + message.sender
                                },
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = LocalResentinChatFontFamily.current,
                                ),
                                color = if (coloredNicklist) {
                                    colorForNick(message.sender, lightTheme)
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = time,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontFamily = LocalResentinChatFontFamily.current,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    if (formatted.isAction) {
                        if (selecting) {
                            SelectionContainer {
                                Text(
                                    text = actionWithTime,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontStyle = FontStyle.Italic,
                                        fontFamily = LocalResentinChatFontFamily.current,
                                    ),
                                )
                            }
                        } else {
                            Text(
                                text = actionWithTime,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontStyle = FontStyle.Italic,
                                    fontFamily = LocalResentinChatFontFamily.current,
                                ),
                            )
                        }
                    } else {
                        if (quoteHead != null) {
                            QuoteHeadBlock(
                                head = quoteHead,
                                barColor = if (isOutgoing) {
                                    MaterialTheme.colorScheme.primary
                                } else if (coloredNicklist) {
                                    colorForNick(message.sender, lightTheme)
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                coloredNicklist = coloredNicklist,
                                lightTheme = lightTheme,
                            )
                        }
                        val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = LocalResentinChatFontFamily.current,
                        )
                        if (selecting) {
                            SelectionContainer { Text(text = bodyWithTime, style = bodyStyle) }
                        } else {
                            Text(text = bodyWithTime, style = bodyStyle)
                        }
                        // Sui propri l'header col nick è ridondante: ora in calce a destra,
                        // come WhatsApp. Il marker (notice) va preservato in calce.
                        if (isOutgoing && !continuesGroup) {
                            Text(
                                text = if (formatted.isNotice) "(notice) · $time" else time,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontFamily = LocalResentinChatFontFamily.current,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                                maxLines = 1,
                            )
                        }
                    }
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
    selecting: Boolean = false,
) {
    val lightTheme = isLightTheme()
    val dccFileHandler = LocalDccFileDownloadHandler.current
    val stripFormatting = LocalStripMircFormatting.current
    val channelClickHandler = LocalIrcChannelLinkHandler.current
    val annotated = remember(
        message.sender, formatted.text, formatted.isAction, formatted.isNotice, prefix, time, coloredNicklist, lightTheme, dccFileHandler, stripFormatting, channelClickHandler,
    ) {
        when {
            formatted.isAction -> buildNickLine("[$time] * ", prefix, message.sender, " ", formatted.text, coloredNicklist, lightTheme, dccFileHandler, stripFormatting, channelClickHandler)
            formatted.isNotice -> buildNickLine("[$time] -", prefix, message.sender, "- ", formatted.text, coloredNicklist, lightTheme, dccFileHandler, stripFormatting, channelClickHandler)
            else -> buildNickLine("[$time] <", prefix, message.sender, "> ", formatted.text, coloredNicklist, lightTheme, dccFileHandler, stripFormatting, channelClickHandler)
        }
    }
    val body: @Composable () -> Unit = {
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalResentinCodeFontFamily.current),
            modifier = Modifier
                .fillMaxWidth()
                .then(mentionHighlight(isMention))
                .padding(horizontal = 16.dp, vertical = density.lineVertical()),
        )
    }
    if (selecting) SelectionContainer { body() } else body()
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
 * row and, past the threshold, prefills the draft on release. IRC has no
 * real threaded replies, so this only ever affects the compose box, never the message.
 * A separate long-press gesture opens the message menu — the drag detector only
 * consumes events once the finger has actually moved, so the two coexist on one row.
 * Pass [gesturesEnabled] = false while the row's text selection is latched. */
@Composable
private fun SwipeToReply(
    onReply: () -> Unit,
    onLongPress: () -> Unit,
    gesturesEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val thresholdPx = with(LocalDensity.current) { REPLY_SWIPE_THRESHOLD_DP.dp.toPx() }

    if (!gesturesEnabled) {
        Box(modifier = Modifier.fillMaxWidth()) { content() }
        return
    }
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
