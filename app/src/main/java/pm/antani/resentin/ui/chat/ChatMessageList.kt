package pm.antani.resentin.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import pm.antani.resentin.R
import pm.antani.resentin.data.db.MemberEntity
import pm.antani.resentin.data.db.MessageEntity
import pm.antani.resentin.data.prefs.ChatDisplayMode
import pm.antani.resentin.data.prefs.MessageDensity

/**
 * F5 — lista messaggi estratta da ChatScreen senza cambi di comportamento:
 * stesso reverse-layout, stesse key/contentType, stesso grouping e divider.
 */
internal data class ChatMessageListData(
    val timelineRows: List<ChatTimelineRow>,
    val members: List<MemberEntity>,
    val highlightPatterns: List<String>?,
    val expandedPresenceBursts: Set<Long>,
)

@Composable
internal fun ChatMessageList(
    listState: LazyListState,
    renderCache: MessageRenderCache,
    data: ChatMessageListData,
    pagedMessages: LazyPagingItems<MessageEntity>? = null,
    dividerIndex: Int?,

    displayMode: ChatDisplayMode,
    messageDensity: MessageDensity,
    showSeconds: Boolean,
    coloredNicklist: Boolean,
    showHostmaskInEvents: Boolean,
    myNick: String?,
    isQuery: Boolean,
    isServer: Boolean,
    viewerUsername: String,

    selectedSearchMessageId: Long?,
    selectingMessageId: Long?,

    onTogglePresence: (Long) -> Unit,
    onReply: (String, String) -> Unit,
    onMessageMenu: (MessageMenuTarget) -> Unit,
    channelKey: String? = null,
    // Runway del send-flight: alza l'intera lista per aprire sotto il varco
    // dove atterra il ghost. Lettura differita (motd-style): il chiamante passa
    // { valore } e la lettura avviene nel graphicsLayer, mai in composition.
    listShift: () -> Float = { 0f },
) {
    val scrolling by remember(listState) {
        derivedStateOf { listState.isScrollInProgress }
    }
    val placeholderHeight = rememberTimelineRowHeight(listState, channelKey)

    val renderedDividerIndex = dividerIndex?.let { data.timelineRows.size - it }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = -listShift() },
        reverseLayout = true,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        if (pagedMessages == null) {
        data.timelineRows.asReversed().forEachIndexed { index, row ->
            val originalIndex = data.timelineRows.lastIndex - index
            if (index == renderedDividerIndex) {
                item(key = "unread-divider", contentType = "unread-divider") {
                    UnreadDivider(density = messageDensity)
                }
            }
            when (row) {
                is ChatTimelineRow.Message -> item(
                    key = row.key,
                    contentType = timelineContentType(row),
                ) {
                    val message = row.message
                    val previous = data.timelineRows.getOrNull(originalIndex - 1)?.messages?.lastOrNull()
                    val gapFromPrevious = previous?.let { message.serverTime - it.serverTime }
                    val tight = previous != null &&
                        originalIndex != dividerIndex &&
                        gapFromPrevious != null && gapFromPrevious in 0..MESSAGE_GROUP_WINDOW_MS &&
                        previous.kind !in SYSTEM_EVENT_KINDS &&
                        message.kind !in SYSTEM_EVENT_KINDS &&
                        previous.sender.equals(message.sender, ignoreCase = true)
                    ChatTimelineMessageItem(
                        message = message,
                        renderCache = renderCache,
                        members = data.members,
                        displayMode = if (isServer) ChatDisplayMode.IRC_LINE else displayMode,
                        density = messageDensity,
                        showSeconds = showSeconds,
                        coloredNicklist = coloredNicklist,
                        showHostmaskInEvents = showHostmaskInEvents,
                        isMention = isMentionRow(message, myNick, isQuery, data.highlightPatterns),
                        isQuery = isQuery,
                        isMine = (myNick ?: viewerUsername).equals(message.sender, ignoreCase = true),
                        isSelected = message.id == selectedSearchMessageId,
                        onReply = onReply,
                        onMessageMenu = onMessageMenu,
                        selectingMessageId = selectingMessageId,
                        tight = tight,
                        deferRichContent = scrolling,
                    )
                }

                is ChatTimelineRow.PresenceSummary -> item(
                    key = row.key,
                    contentType = "presence-summary",
                ) {
                    val burstKey = row.messages.first().id
                    val selectedInBurst = row.messages.any { it.id == selectedSearchMessageId }
                    val expanded = selectedInBurst || burstKey in data.expandedPresenceBursts
                    val uniqueUsers = row.messages.map { it.sender.lowercase() }.distinct().size
                    val senderLabel = row.sender ?: pluralStringResource(
                        R.plurals.chat_activity_users,
                        uniqueUsers,
                        uniqueUsers,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        PresenceBurstSummaryRow(
                            sender = senderLabel,
                            joins = row.joins,
                            leaves = row.leaves,
                            time = formatTime(row.messages.last().serverTime, showSeconds),
                            isIrcLine = isServer || displayMode == ChatDisplayMode.IRC_LINE,
                            expanded = expanded,
                            selected = selectedInBurst,
                            onToggle = { onTogglePresence(burstKey) },
                        )
                        if (expanded) {
                            row.messages.forEach { event ->
                                ChatTimelineMessageItem(
                                    message = event,
                                    renderCache = renderCache,
                                    members = data.members,
                                    displayMode = if (isServer) ChatDisplayMode.IRC_LINE else displayMode,
                                    density = messageDensity,
                                    showSeconds = showSeconds,
                                    coloredNicklist = coloredNicklist,
                                    showHostmaskInEvents = showHostmaskInEvents,
                                    isMention = false,
                                    isQuery = isQuery,
                                    isMine = (myNick ?: viewerUsername).equals(event.sender, ignoreCase = true),
                                    isSelected = event.id == selectedSearchMessageId,
                                    onReply = onReply,
                                    onMessageMenu = onMessageMenu,
                                    selectingMessageId = selectingMessageId,
                                    tight = false,
                                    deferRichContent = scrolling,
                                )
                            }
                        }
                    }
                }
            }
        }
        } else {
            val pagingItems = pagedMessages
            items(
                count = pagingItems.itemCount,
                key = pagingItems.itemKey { it.id },
                contentType = pagingItems.itemContentType { pagingContentType(it) },
            ) { index ->
                val message = pagingItems[index]
                if (message == null) {
                    Spacer(Modifier.fillMaxWidth().height(placeholderHeight()))
                } else {
                    ChatTimelineMessageItem(
                        message = message,
                        renderCache = renderCache,
                        members = data.members,
                        displayMode = if (isServer) ChatDisplayMode.IRC_LINE else displayMode,
                        density = messageDensity,
                        showSeconds = showSeconds,
                        coloredNicklist = coloredNicklist,
                        showHostmaskInEvents = showHostmaskInEvents,
                        isMention = isMentionRow(message, myNick, isQuery, data.highlightPatterns),
                        isQuery = isQuery,
                        isMine = (myNick ?: viewerUsername).equals(message.sender, ignoreCase = true),
                        isSelected = message.id == selectedSearchMessageId,
                        onReply = onReply,
                        onMessageMenu = onMessageMenu,
                        selectingMessageId = selectingMessageId,
                        tight = false,
                        deferRichContent = scrolling,
                    )
                }
            }
        }
    }
}

internal fun pagingContentType(message: MessageEntity): String = if (message.kind in SYSTEM_EVENT_KINDS) "system-message" else "chat-message"

internal fun timelineContentType(row: ChatTimelineRow): String = when (row) {
    is ChatTimelineRow.Message ->
        if (row.message.kind in SYSTEM_EVENT_KINDS) "system-message" else "chat-message"
    is ChatTimelineRow.PresenceSummary -> "presence-summary"
}
