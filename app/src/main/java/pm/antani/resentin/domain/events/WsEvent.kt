package pm.antani.resentin.domain.events

import kotlinx.serialization.json.JsonObject
import pm.antani.resentin.net.dto.AutoAwayDebounceDto
import pm.antani.resentin.net.dto.AvatarReadyDto
import pm.antani.resentin.net.dto.AwayConfirmedDto
import pm.antani.resentin.net.dto.BanlistBundleDto
import pm.antani.resentin.net.dto.ChannelModesChangedDto
import pm.antani.resentin.net.dto.DccOfferDto
import pm.antani.resentin.net.dto.DccOfferResolvedDto
import pm.antani.resentin.net.dto.DirectoryCompleteDto
import pm.antani.resentin.net.dto.DirectoryFailedDto
import pm.antani.resentin.net.dto.DirectoryProgressDto
import pm.antani.resentin.net.dto.IsupportChangedDto
import pm.antani.resentin.net.dto.LusersBundleDto
import pm.antani.resentin.net.dto.MembersSeededDto
import pm.antani.resentin.net.dto.QueryWindowsListDto
import pm.antani.resentin.net.dto.ScrollbackMessageDto
import pm.antani.resentin.net.dto.TopicChangedDto
import pm.antani.resentin.net.dto.WebSessionSeveredDto
import pm.antani.resentin.net.dto.WhoReplyDto
import pm.antani.resentin.net.dto.WhoisBundleDto
import pm.antani.resentin.net.dto.WhowasBundleDto
import pm.antani.resentin.net.dto.WindowInviteDeclinedDto
import pm.antani.resentin.net.dto.WindowInvitedDto

sealed interface WsEvent {
    data class MessageReceived(val message: ScrollbackMessageDto) : WsEvent
    data class IsupportChanged(val isupport: IsupportChangedDto) : WsEvent
    data class MembersSeeded(val seeded: MembersSeededDto) : WsEvent
    data class WhoisBundle(val whois: WhoisBundleDto) : WsEvent

    /** Incremental WHOIS-card patch for a peer avatar fetch that finished after the
     * bundle (M3b) — the card updates in place if still open on that nick. */
    data class AvatarReady(val avatar: AvatarReadyDto) : WsEvent

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

    /** The `away` verb landed — explicit away set or cleared for one network
     * (cicchetto's `away_confirmed`, mirrored into per-network away state). */
    data class AwayConfirmed(val away: AwayConfirmedDto) : WsEvent

    /** Reply to a `whowas` verb query — one ephemeral bundle per nick. */
    data class WhowasBundle(val whowas: WhowasBundleDto) : WsEvent

    /** Reply to a `who` verb query — the folded 352 burst for one target. */
    data class WhoReply(val who: WhoReplyDto) : WsEvent

    /** Reply to an `lusers` verb query — the RFC 2812 §3.4.2 counters. The
     * server also auto-emits one on connect welcome, which receivers must drop
     * unless they asked (same consume-once gate as cicchetto's lusersBundle). */
    data class LusersBundle(val lusers: LusersBundleDto) : WsEvent

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

    /** The server's NORMALIZED services-identity verdict for one network
     * (cicchetto #388 parity): folds bahamut's `+r` umode, OFTC's `+R`, IRCv3
     * `account-notify` and numeric 330 into one `identified` boolean. Both the
     * live edge and the cold user-topic snapshot ride this event, so a reload
     * mid-session re-learns the verdict. `account` is display-only — never
     * infer identity from it (or from any umode letter), ask [identified]. */
    data class SessionIdentityChanged(
        val networkId: Int,
        val identified: Boolean,
        val account: String?,
    ) : WsEvent

    /** Inbound IRC INVITE not yet acted on — pushed live and re-sent as a cold-subscribe
     * snapshot on the per-user topic for every still-`:invited` channel. */
    data class WindowInvited(val invite: WindowInvitedDto) : WsEvent

    /** The invite for a channel was declined (this device or another one) — drop its banner. */
    data class WindowInviteDeclined(val declined: WindowInviteDeclinedDto) : WsEvent

    /** A peer's `DCC SEND` is being held awaiting Accept/Decline — issue 2089 on grappa-irc. */
    data class DccOffer(val offer: DccOfferDto) : WsEvent

    /** A held DCC offer left the held set (accepted, declined, or expired) — drop its banner. */
    data class DccOfferResolved(val resolved: DccOfferResolvedDto) : WsEvent

    data class DirectoryProgress(val progress: DirectoryProgressDto) : WsEvent
    data class DirectoryComplete(val complete: DirectoryCompleteDto) : WsEvent
    data class DirectoryFailed(val failed: DirectoryFailedDto) : WsEvent

    data class Unknown(val kind: String?, val raw: JsonObject) : WsEvent
}
