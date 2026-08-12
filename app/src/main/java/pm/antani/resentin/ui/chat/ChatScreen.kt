package pm.antani.resentin.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import pm.antani.resentin.R
import pm.antani.resentin.data.db.MemberEntity
import pm.antani.resentin.data.db.MessageEntity
import pm.antani.resentin.data.prefs.ChatDisplayMode
import pm.antani.resentin.irc.FormattedEvent
import pm.antani.resentin.irc.SystemEventFormatter
import pm.antani.resentin.irc.highestSigil
import pm.antani.resentin.net.AppJson
import pm.antani.resentin.ui.common.MircText
import pm.antani.resentin.ui.common.UserCardSheet
import pm.antani.resentin.ui.common.sigilsOf

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
    viewerUsername: String,
    isQuery: Boolean = false,
    onBack: () -> Unit,
    onMembersClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onOpenQuery: (networkSlug: String, nick: String) -> Unit,
) {
    val messages by viewModel.messages.collectAsState()
    val topic by viewModel.topic.collectAsState()
    val channelModes by viewModel.channelModes.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val error by viewModel.error.collectAsState()
    val whois by viewModel.selectedWhois.collectAsState()
    val ownSigils by viewModel.ownSigils.collectAsState()
    val privilegeModes by viewModel.privilegeModes.collectAsState()
    val initialReadCursor by viewModel.initialReadCursor.collectAsState()
    val initialReadCursorReady by viewModel.initialReadCursorReady.collectAsState()
    val members by viewModel.members.collectAsState()
    val displayMode by viewModel.chatDisplayMode.collectAsState()
    val showSeconds by viewModel.showSeconds.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val listState = rememberLazyListState()
    var hasScrolledInitially by remember { mutableStateOf(false) }
    var showTopicDialog by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::uploadFile)
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToQuery.collect { nick -> onOpenQuery(networkSlug, nick) }
    }

    // Position (within `messages`) of the first message past where the reader left off —
    // also where the "Hai letto fino a qui" divider renders. Derived from
    // initialReadCursor, which is frozen for this screen's whole lifetime (see
    // ChatViewModel), so the divider stays put even as newly-arrived messages get
    // marked read live: it marks where you left off, not a constantly-advancing cursor.
    // Null when there's nothing to mark (cursor still loading, never read anything, or
    // everything is already read).
    val dividerIndex = initialReadCursor?.let { cursor ->
        messages.indexOfFirst { it.id > cursor }.takeIf { it >= 0 }
    }

    // Land on the first unread message (per the server's read-cursor), not always the
    // bottom — only once, and only once we actually know where that is (see
    // ChatViewModel.initialReadCursorReady: null is ambiguous between "not loaded yet"
    // and "never read anything").
    LaunchedEffect(messages, initialReadCursorReady) {
        if (hasScrolledInitially || !initialReadCursorReady || messages.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(dividerIndex ?: (messages.size - 1))
        hasScrolledInitially = true
    }

    // Only jump to bottom when a new message lands at the tail (after the initial
    // placement above) — loadOlder() prepends at the head and must not yank the view
    // back down. +1 accounts for the divider's own LazyColumn row when present.
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (hasScrolledInitially && messages.isNotEmpty()) {
            val lastIndex = messages.size - 1 + if (dividerIndex != null) 1 else 0
            listState.animateScrollToItem(lastIndex)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index -> if (index == 0 && messages.isNotEmpty()) viewModel.loadOlder() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.clickable(enabled = topic != null) { showTopicDialog = true },
                    ) {
                        Text(title)
                        // "(+rnt) topic text" — modes prefix the topic line the way a
                        // classic IRC client's status bar does, shown even without a
                        // topic set so the channel's mode flags stay visible either way.
                        val subtitle = buildString {
                            if (channelModes != null) append("($channelModes) ")
                            if (topic != null) append(topic)
                        }.takeIf { it.isNotBlank() }
                        if (subtitle != null) {
                            MircText(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (!isQuery) {
                        IconButton(onClick = onMembersClick) {
                            Icon(Icons.Default.Person, contentDescription = stringResource(R.string.cd_members))
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_channel_settings))
                        }
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { filePicker.launch("*/*") },
                    enabled = !isUploading,
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AttachFile, contentDescription = stringResource(R.string.cd_attach_file))
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = viewModel::onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.chat_message_placeholder)) },
                )
                IconButton(onClick = viewModel::send) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.cd_send))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                messages.forEachIndexed { index, message ->
                    if (index == dividerIndex) {
                        item(key = "unread-divider") { UnreadDivider() }
                    }
                    item(key = message.id) {
                        MessageRow(
                            message = message,
                            members = members,
                            displayMode = displayMode,
                            showSeconds = showSeconds,
                            onReply = viewModel::reply,
                            onLongPress = viewModel::onMessageLongPress,
                        )
                    }
                }
            }
            error?.let { message ->
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    Text(message)
                }
            }
        }
    }

    val whoisValue = whois
    if (whoisValue != null) {
        UserCardSheet(
            whois = whoisValue,
            viewerUsername = viewerUsername,
            ownSigils = ownSigils,
            targetSigils = viewModel.sigilsFor(whoisValue.target),
            availableModes = privilegeModes,
            onDismiss = viewModel::dismissWhois,
            onContactPrivately = viewModel::contactPrivately,
            onKick = viewModel::kickFromCard,
            onBan = viewModel::banFromCard,
            onSetMode = viewModel::setModeFromCard,
            showChannelActions = !isQuery,
        )
    }

    if (showTopicDialog && topic != null) {
        AlertDialog(
            onDismissRequest = { showTopicDialog = false },
            title = { Text(stringResource(R.string.chat_topic_dialog_title)) },
            text = { MircText(text = topic!!, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showTopicDialog = false }) {
                    Text(stringResource(R.string.chat_dialog_close))
                }
            },
        )
    }
}

@Composable
private fun UnreadDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
        Text(
            text = stringResource(R.string.chat_unread_divider),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
    }
}

/** The nick's highest-priority role sigil (~&@%+), or "" if they hold none / aren't a
 * known member (e.g. a query partner, who never appears in a channel's member list). */
private fun nickPrefixFor(nick: String, members: List<MemberEntity>): String {
    val member = members.find { it.nick.equals(nick, ignoreCase = true) } ?: return ""
    return highestSigil(sigilsOf(member))?.toString().orEmpty()
}

@Composable
private fun reasonSuffix(reason: String?): String =
    if (reason == null) "" else stringResource(R.string.paren_suffix, reason)

/** Renders a structured [FormattedEvent.System] into its localized display line — the
 * templates themselves live in strings.xml so this varies by locale. */
@Composable
private fun systemEventText(event: FormattedEvent.System): String = when (event) {
    is FormattedEvent.System.Join -> stringResource(R.string.event_join, event.sender)
    is FormattedEvent.System.Part -> stringResource(R.string.event_part, event.sender, reasonSuffix(event.reason))
    is FormattedEvent.System.Quit -> stringResource(R.string.event_quit, event.sender, reasonSuffix(event.reason))
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
    showSeconds: Boolean,
    onReply: (String) -> Unit,
    onLongPress: (String) -> Unit,
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
            MircText(
                text = "${systemEventText(formatted)} · $time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        is FormattedEvent.Chat -> {
            SwipeToReply(onReply = { onReply(message.sender) }, onLongPress = { onLongPress(message.sender) }) {
                if (displayMode == ChatDisplayMode.IRC_LINE) {
                    IrcLineRow(message, formatted, prefix, time)
                } else {
                    BubbleRow(message, formatted, prefix, time)
                }
            }
        }
    }
}

@Composable
private fun BubbleRow(message: MessageEntity, formatted: FormattedEvent.Chat, prefix: String, time: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        if (formatted.isAction) {
            Row(verticalAlignment = Alignment.Bottom) {
                MircText(
                    text = "* $prefix${message.sender} ${formatted.text}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                    modifier = Modifier.weight(1f),
                )
                Text(text = time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (formatted.isNotice) "$prefix${message.sender} (notice)" else "$prefix${message.sender}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(text = time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MircText(text = formatted.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** `[HH:mm] <nick> message`, classic IRC-client style. Role prefix (if any) sits right
 * inside the angle brackets — `<@nick>` — matching how real IRC clients render it. */
@Composable
private fun IrcLineRow(message: MessageEntity, formatted: FormattedEvent.Chat, prefix: String, time: String) {
    val line = when {
        formatted.isAction -> "[$time] * $prefix${message.sender} ${formatted.text}"
        formatted.isNotice -> "[$time] -$prefix${message.sender}- ${formatted.text}"
        else -> "[$time] <$prefix${message.sender}> ${formatted.text}"
    }
    MircText(
        text = line,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    )
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
            Icons.AutoMirrored.Filled.ArrowForward,
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
