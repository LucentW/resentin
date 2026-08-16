package pm.antani.resentin.domain.events

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import pm.antani.resentin.net.AppJson
import pm.antani.resentin.net.dto.AutoAwayDebounceDto
import pm.antani.resentin.net.dto.BanlistBundleDto
import pm.antani.resentin.net.dto.ChannelModesChangedDto
import pm.antani.resentin.net.dto.IsupportChangedDto
import pm.antani.resentin.net.dto.MembersSeededDto
import pm.antani.resentin.net.dto.MessageEventPayloadDto
import pm.antani.resentin.net.dto.QueryWindowsListDto
import pm.antani.resentin.net.dto.TopicChangedDto
import pm.antani.resentin.net.dto.WebSessionSeveredDto
import pm.antani.resentin.net.dto.WhoisBundleDto

/**
 * Decodes a raw event-frame payload into a typed [WsEvent], per the additive-only
 * wire contract: an unrecognised `kind`, or a known `kind` with a malformed payload,
 * must never be fatal — both fall back to [WsEvent.Unknown] rather than throwing.
 */
object WsEventDecoder {
    fun decode(raw: JsonObject, topic: String): WsEvent {
        val kind = raw["kind"]?.jsonPrimitive?.contentOrNull
        return try {
            when (kind) {
                "read_cursor_set" -> WsEvent.ReadCursorSet(
                    topic = topic,
                    lastReadMessageId = raw.getValue("last_read_message_id").jsonPrimitive.long,
                    badgeCount = raw["badge_count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                )
                "window_counts" -> WsEvent.WindowCountsChanged(
                    topic = topic,
                    channel = raw.getValue("channel").jsonPrimitive.content,
                    messages = raw["messages"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                    mentions = raw["mentions"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                    severity = raw["severity"]?.jsonPrimitive?.contentOrNull ?: "none",
                )
                "message" -> WsEvent.MessageReceived(
                    AppJson.decodeFromJsonElement(MessageEventPayloadDto.serializer(), raw).message,
                )
                "isupport_changed" -> WsEvent.IsupportChanged(
                    AppJson.decodeFromJsonElement(IsupportChangedDto.serializer(), raw),
                )
                "members_seeded", "names_reply" -> WsEvent.MembersSeeded(
                    AppJson.decodeFromJsonElement(MembersSeededDto.serializer(), raw),
                )
                "whois_bundle" -> WsEvent.WhoisBundle(
                    AppJson.decodeFromJsonElement(WhoisBundleDto.serializer(), raw),
                )
                "banlist_bundle" -> WsEvent.BanlistBundle(
                    AppJson.decodeFromJsonElement(BanlistBundleDto.serializer(), raw),
                )
                "topic_changed" -> WsEvent.TopicChanged(
                    AppJson.decodeFromJsonElement(TopicChangedDto.serializer(), raw),
                )
                "channel_modes_changed" -> WsEvent.ChannelModesChanged(
                    AppJson.decodeFromJsonElement(ChannelModesChangedDto.serializer(), raw),
                )
                "web_session_severed" -> WsEvent.WebSessionSevered(
                    AppJson.decodeFromJsonElement(WebSessionSeveredDto.serializer(), raw),
                )
                "auto_away_debounce_changed" -> WsEvent.AutoAwayDebounceChanged(
                    AppJson.decodeFromJsonElement(AutoAwayDebounceDto.serializer(), raw),
                )
                "query_windows_list" -> WsEvent.QueryWindowsListReceived(
                    AppJson.decodeFromJsonElement(QueryWindowsListDto.serializer(), raw),
                )
                else -> WsEvent.Unknown(kind, raw)
            }
        } catch (e: Exception) {
            WsEvent.Unknown(kind, raw)
        }
    }
}
