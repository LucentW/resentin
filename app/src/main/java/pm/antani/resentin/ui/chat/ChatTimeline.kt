package pm.antani.resentin.ui.chat

import pm.antani.resentin.data.db.MessageEntity
import java.util.Locale

sealed interface ChatTimelineRow {
    val messages: List<MessageEntity>
    val key: String

    data class Message(val message: MessageEntity) : ChatTimelineRow {
        override val messages: List<MessageEntity> = listOf(message)
        override val key: String = "message-${message.id}"
    }

    sealed interface PresenceSummary : ChatTimelineRow {
        val joins: Int
        val leaves: Int
        val sender: String?
    }

    data class PresenceBurst(override val messages: List<MessageEntity>) : PresenceSummary {
        override val key: String = "presence-${messages.first().id}"
        override val joins: Int = messages.count { it.kind == "join" }
        override val leaves: Int = messages.count { it.kind == "part" || it.kind == "quit" }
        override val sender: String = messages.first().sender
    }

    data class SuppressedPresence(override val messages: List<MessageEntity>) : PresenceSummary {
        override val key: String = "suppressed-presence-${messages.first().id}"
        override val joins: Int = messages.count { it.kind == "join" }
        override val leaves: Int = messages.count { it.kind == "part" || it.kind == "quit" }
        override val sender: String? = messages.map { it.sender.lowercase() }.distinct().singleOrNull()
            ?.let { messages.first().sender }
    }
}

/**
 * In smart mode, presence changes from a nick are shown only if that nick has
 * spoken in the channel during the previous [activeWindowMs]. Quiet presence
 * bursts are represented by an expandable summary. All original rows remain in
 * Room and are available in the Activity view.
 */
fun buildChatTimeline(
    messages: List<MessageEntity>,
    readCursor: Long?,
    smartPresenceFilterEnabled: Boolean = false,
    alwaysVisibleSender: String? = null,
    activeWindowMs: Long = SMART_PRESENCE_ACTIVE_WINDOW_MS,
    maxGapMs: Long = PRESENCE_BURST_MAX_GAP_MS,
    maxSpanMs: Long = PRESENCE_BURST_MAX_SPAN_MS,
): List<ChatTimelineRow> {
    val rows = mutableListOf<ChatTimelineRow>()
    val lastChatActivityBySender = mutableMapOf<String, Long>()
    var index = 0

    while (index < messages.size) {
        val first = messages[index]
        if (!first.isPresenceTransition()) {
            rows += ChatTimelineRow.Message(first)
            if (first.kind !in SYSTEM_EVENT_KINDS) {
                lastChatActivityBySender[first.sender.lowercase(Locale.ROOT)] = first.serverTime
            }
            index++
            continue
        }

        if (smartPresenceFilterEnabled && !first.sender.equals(alwaysVisibleSender, ignoreCase = true) &&
            !hasRecentChatActivity(first.sender, first.serverTime, lastChatActivityBySender, activeWindowMs)
        ) {
            val suppressed = mutableListOf(first)
            var nextIndex = index + 1
            while (nextIndex < messages.size) {
                val previous = suppressed.last()
                val candidate = messages[nextIndex]
                val gap = candidate.serverTime - previous.serverTime
                val span = candidate.serverTime - first.serverTime
                val crossesReadCursor = readCursor != null && (candidate.id > readCursor) != (first.id > readCursor)
                if (!candidate.isPresenceTransition() ||
                    candidate.sender.equals(alwaysVisibleSender, ignoreCase = true) ||
                    hasRecentChatActivity(
                        candidate.sender,
                        candidate.serverTime,
                        lastChatActivityBySender,
                        activeWindowMs,
                    ) ||
                    gap !in 0..maxGapMs || span !in 0..maxSpanMs ||
                    crossesReadCursor
                ) {
                    break
                }
                suppressed += candidate
                nextIndex++
            }

            // One-off joins/leaves fade into the background completely. Repeated
            // churn still gets a compact, expandable marker at its original time.
            if (suppressed.size >= MIN_SUPPRESSED_PRESENCE_SUMMARY_SIZE) {
                rows += ChatTimelineRow.SuppressedPresence(suppressed)
            }
            index = nextIndex
            continue
        }

        val burst = mutableListOf(first)
        var nextIndex = index + 1
        while (nextIndex < messages.size) {
            val previous = burst.last()
            val candidate = messages[nextIndex]
            val gap = candidate.serverTime - previous.serverTime
            val span = candidate.serverTime - first.serverTime
            val crossesReadCursor = readCursor != null && (candidate.id > readCursor) != (first.id > readCursor)
            if (!candidate.isPresenceTransition() ||
                !candidate.sender.equals(first.sender, ignoreCase = true) ||
                gap !in 0..maxGapMs || span !in 0..maxSpanMs ||
                crossesReadCursor
            ) {
                break
            }
            burst += candidate
            nextIndex++
        }

        val hasJoin = burst.any { it.kind == "join" }
        val hasLeave = burst.any { it.kind == "part" || it.kind == "quit" }
        if (burst.size >= MIN_PRESENCE_BURST_SIZE && hasJoin && hasLeave) {
            rows += ChatTimelineRow.PresenceBurst(burst)
        } else {
            burst.forEach { rows += ChatTimelineRow.Message(it) }
        }
        index = nextIndex
    }

    return rows
}

private fun hasRecentChatActivity(
    sender: String,
    eventTime: Long,
    lastChatActivityBySender: Map<String, Long>,
    activeWindowMs: Long,
): Boolean {
    val lastMessageTime = lastChatActivityBySender[sender.lowercase(Locale.ROOT)] ?: return false
    val age = eventTime - lastMessageTime
    return age in 0..activeWindowMs
}

private fun MessageEntity.isPresenceTransition(): Boolean =
    kind == "join" || kind == "part" || kind == "quit"

private val SYSTEM_EVENT_KINDS = setOf("join", "part", "quit", "kick", "mode", "nick_change", "topic")

private const val MIN_PRESENCE_BURST_SIZE = 3
private const val MIN_SUPPRESSED_PRESENCE_SUMMARY_SIZE = 2
private const val SMART_PRESENCE_ACTIVE_WINDOW_MS = 10 * 60_000L
private const val PRESENCE_BURST_MAX_GAP_MS = 30_000L
private const val PRESENCE_BURST_MAX_SPAN_MS = 120_000L
