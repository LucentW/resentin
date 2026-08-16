package pm.antani.resentin.domain.events

import kotlinx.serialization.json.JsonObject
import pm.antani.resentin.net.dto.AutoAwayDebounceDto
import pm.antani.resentin.net.dto.BanlistBundleDto
import pm.antani.resentin.net.dto.ChannelModesChangedDto
import pm.antani.resentin.net.dto.IsupportChangedDto
import pm.antani.resentin.net.dto.MembersSeededDto
import pm.antani.resentin.net.dto.QueryWindowsListDto
import pm.antani.resentin.net.dto.ScrollbackMessageDto
import pm.antani.resentin.net.dto.TopicChangedDto
import pm.antani.resentin.net.dto.WebSessionSeveredDto
import pm.antani.resentin.net.dto.WhoisBundleDto

sealed interface WsEvent {
    data class MessageReceived(val message: ScrollbackMessageDto) : WsEvent
    data class IsupportChanged(val isupport: IsupportChangedDto) : WsEvent
    data class MembersSeeded(val seeded: MembersSeededDto) : WsEvent
    data class WhoisBundle(val whois: WhoisBundleDto) : WsEvent

    /** Reply to a `banlist` verb query for one type-A list mode of a channel. */
    data class BanlistBundle(val bundle: BanlistBundleDto) : WsEvent
    data class TopicChanged(val topic: TopicChangedDto) : WsEvent

    /** Pushed by the server right after a channel join (mirrors `topic_changed`'s
     * push-if-cached timing) and again live on every `MODE` line for the channel. */
    data class ChannelModesChanged(val payload: ChannelModesChangedDto) : WsEvent
    data class WebSessionSevered(val severed: WebSessionSeveredDto) : WsEvent

    /** #348 on grappa-irc — the subject's auto-away preference moved, including for a
     * write this device just made (the server never lets a client originate this state,
     * only mirror it — same push fires for every device on the subject's account). */
    data class AutoAwayDebounceChanged(val debounce: AutoAwayDebounceDto) : WsEvent
    data class QueryWindowsListReceived(val windows: QueryWindowsListDto) : WsEvent

    /** `read_cursor_set` carries no network/channel in its payload — only in the topic
     * it arrives on (`grappa:user:{u}/network:{slug}/channel:{chan}`), so [topic] is
     * threaded through from the join/event source rather than decoded from JSON. */
    data class ReadCursorSet(val topic: String, val lastReadMessageId: Long, val badgeCount: Int) : WsEvent

    /** The `window_counts` unread-badge snapshot for one channel, pushed live on every
     * new message. Carries `channel` itself, but not the network — [topic] supplies that. */
    data class WindowCountsChanged(
        val topic: String,
        val channel: String,
        val messages: Int,
        val mentions: Int,
        val severity: String,
    ) : WsEvent

    data class Unknown(val kind: String?, val raw: JsonObject) : WsEvent
}
