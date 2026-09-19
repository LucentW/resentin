package pm.antani.resentin.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import pm.antani.resentin.data.db.MemberEntity
import pm.antani.resentin.data.db.MessageEntity
import pm.antani.resentin.data.prefs.MessageDensity
import pm.antani.resentin.irc.FormattedEvent
import pm.antani.resentin.ui.theme.LocalResentinChatFontFamily
import kotlin.math.roundToInt

/** Tap-launched ghost payload: snapshot text plus a token to disambiguate sends. */
data class SendFlight(val text: String, val token: Long)

/** Full flight duration: morph out of the composer, then rise to the slot. */
internal const val SEND_FLIGHT_DURATION_MS = 480

/** Flights longer than this stay put — no giant overlay for pastes. */
internal const val SEND_FLIGHT_MAX_CHARS = 200

/** Morph occupies the first 45% of the flight (text slides, bubble grows). */
internal const val SEND_FLIGHT_MORPH_END = 0.45f

/** Bubble replica dissolves in over 0.45 -> 0.85 (motd's morph-swap window). */
internal const val SEND_FLIGHT_SWAP_START = 0.45f
internal const val SEND_FLIGHT_SWAP_END = 0.85f

internal fun smoothstep01(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

/** Morph clock 0 -> 1 over the first stretch of the flight. */
internal fun sendFlightMorph(progress: Float): Float =
    smoothstep01(progress / SEND_FLIGHT_MORPH_END)

/** Bubble-replica opacity 0 -> 1 over the swap window. */
internal fun sendFlightSwap(progress: Float): Float =
    smoothstep01((progress - SEND_FLIGHT_SWAP_START) / (SEND_FLIGHT_SWAP_END - SEND_FLIGHT_SWAP_START))

/** Rise 0 -> 1, fast start and soft landing across the whole flight. */
internal fun sendFlightRise(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return 1f - (1f - p) * (1f - p)
}

/**
 * The send-flight ghost, motd-style in two phases:
 *
 * 1. morph — the tapped line stays pinned glyph-for-glyph where the composer
 *    had it (full opacity, no pop), slides toward the outgoing side while the
 *    ink crossfades from field to bubble;
 * 2. flight — the identical [BubbleRow] replica (same renderer as the landing
 *    row, so it cannot drift) dissolves in and rides up to the tail slot.
 *
 * [start] is the composer glyph origin in list-box coordinates, measured at
 * tap time. The overlay is invisible to semantics — its text duplicates the
 * arriving real row.
 */
@Composable
internal fun BoxScope.SendFlightOverlay(
    text: String,
    senderNick: String,
    networkSlug: String,
    channelName: String,
    // Deferred reads ONLY: the caller passes { flightProgress.value } so every
    // per-frame read below happens in a draw/layout lambda (motd-style) and
    // never recomposes the chat while the flight runs.
    progress: () -> Float,
    start: Offset,
    boxSize: IntSize,
    renderCache: MessageRenderCache,
    members: List<MemberEntity>,
    density: MessageDensity,
    showSeconds: Boolean,
    coloredNicklist: Boolean,
    tight: Boolean,
    sentAt: Long,
    onMeasuredHeightPx: (Int) -> Unit = {},
) {
    val dens = LocalDensity.current
    val ghost = remember(text, senderNick, sentAt) {
        MessageEntity(
            networkSlug = networkSlug,
            channelName = channelName,
            id = Long.MIN_VALUE,
            serverTime = sentAt,
            kind = "privmsg",
            sender = senderNick,
            body = text,
        )
    }
    val formatted = remember(ghost) { renderCache.formatted(ghost) as? FormattedEvent.Chat } ?: return
    var textWidthPx by remember(text) { mutableStateOf<Int?>(null) }
    var ghostHeightPx by remember(text) { mutableStateOf<Int?>(null) }

    val endPaddingPx = with(dens) { 12.dp.toPx() }
    val bottomGapPx = with(dens) { 8.dp.toPx() }
    val fallbackTargetY = boxSize.height - with(dens) { 64.dp.toPx() }
    val startPad = with(dens) { start.x.toDp() }
    val bubblePrefix = remember(senderNick, members) { nickPrefixFor(senderNick, members) }
    val ghostTime = remember(sentAt, showSeconds) { formatTime(sentAt, showSeconds) }
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = LocalResentinChatFontFamily.current,
    )
    val fieldInk = MaterialTheme.colorScheme.onSurface
    val bubbleInk = MaterialTheme.colorScheme.onPrimaryContainer

    Box(Modifier.matchParentSize().clearAndSetSemantics {}) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    val p = progress()
                    val target = ghostHeightPx?.let { boxSize.height - it - bottomGapPx }
                        ?: fallbackTargetY
                    IntOffset(0, (start.y + (target - start.y) * sendFlightRise(p)).roundToInt())
                }
                .onSizeChanged {
                    ghostHeightPx = it.height
                    onMeasuredHeightPx(it.height)
                },
        ) {
            Box(
                modifier = Modifier.graphicsLayer { alpha = sendFlightSwap(progress()) },
            ) {
                BubbleRow(
                    message = ghost,
                    renderCache = renderCache,
                    formatted = formatted,
                    prefix = bubblePrefix,
                    time = ghostTime,
                    coloredNicklist = coloredNicklist,
                    isMention = false,
                    isMine = true,
                    tight = tight,
                    density = density,
                )
            }
            // Morph stand-in: the tapped line itself, sliding toward the
            // outgoing side. At most two layers overlap at any time: the field
            // ink dissolves over the morph clock, the bubble ink over the swap
            // window into the replica — never the triple-ink double text.
            Box {
                Text(
                    text = text,
                    style = textStyle,
                    color = fieldInk,
                    maxLines = 1,
                    onTextLayout = { textWidthPx = it.size.width },
                    modifier = Modifier
                        .padding(start = startPad, end = 12.dp)
                        .graphicsLayer {
                            val p = progress()
                            val slide = ((boxSize.width - (textWidthPx ?: 0) - endPaddingPx) - start.x)
                                .coerceAtLeast(0f)
                            translationX = slide * sendFlightMorph(p)
                            alpha = (1f - sendFlightMorph(p)) * (1f - sendFlightSwap(p))
                        },
                )
                Text(
                    text = text,
                    style = textStyle,
                    color = bubbleInk,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = startPad, end = 12.dp)
                        .graphicsLayer {
                            val p = progress()
                            val slide = ((boxSize.width - (textWidthPx ?: 0) - endPaddingPx) - start.x)
                                .coerceAtLeast(0f)
                            translationX = slide * sendFlightMorph(p)
                            alpha = sendFlightMorph(p) * (1f - sendFlightSwap(p))
                        },
                )
            }
        }
    }
}

/** Whether a tapped draft may fly: single short user line, never a command. */
internal fun sendFlightEligible(
    text: String,
    displayIsIrcLine: Boolean,
    isServer: Boolean,
): Boolean =
    text.isNotBlank() &&
        '\n' !in text &&
        !text.trimStart().startsWith("/") &&
        text.length <= SEND_FLIGHT_MAX_CHARS &&
        !displayIsIrcLine &&
        !isServer

/**
 * Ghost grouping, mirroring ChatMessageList's `tight` rule for the row the
 * message WILL become (appended after [previous]): same sender, same window,
 * no system kinds. The divider check is trivially true for an appended row.
 * A ghost that disagrees with its landing row flips ora-inline/ora-sotto at
 * handoff — exactly the extra animation this exists to remove.
 */
internal fun ghostTightFor(
    previous: MessageEntity?,
    sentAt: Long,
    senderNick: String,
): Boolean {
    if (previous == null) return false
    val gap = sentAt - previous.serverTime
    return gap in 0..MESSAGE_GROUP_WINDOW_MS &&
        previous.kind !in SYSTEM_EVENT_KINDS &&
        previous.sender.equals(senderNick, ignoreCase = true)
}
