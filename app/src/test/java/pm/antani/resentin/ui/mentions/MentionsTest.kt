package pm.antani.resentin.ui.mentions

import org.junit.Assert.assertEquals
import org.junit.Test
import pm.antani.resentin.net.AppJson
import pm.antani.resentin.net.dto.MentionsBundleDto
import pm.antani.resentin.net.dto.MentionsMessageDto

class MentionsTest {
    @Test
    fun groupsPreserveFirstSeenChannelOrder() {
        val rows = listOf(
            MentionsMessageDto(3L, "#b", "alice", "hi"),
            MentionsMessageDto(1L, "#a", "bob", "yo"),
            MentionsMessageDto(2L, "#a", "carol", "hey"),
        )
        val groups = groupMentionsByChannel(rows)
        assertEquals(listOf("#b", "#a"), groups.map { it.channel })
        assertEquals(1, groups[0].rows.size)
        assertEquals(2, groups[1].rows.size)
    }

    @Test
    fun bundleDecodesSnakeCaseWireShape() {
        val dto = AppJson.decodeFromString(
            MentionsBundleDto.serializer(),
            """{"kind":"mentions_bundle","network":"azzurra","away_started_at":"2026-09-01T10:00:00Z",
              |"away_ended_at":"2026-09-01T11:00:00Z","away_reason":null,
              |"messages":[{"server_time":1725,"channel":"#italia","sender":"alice","body":"ciao bob","kind":"privmsg"}]}""".trimMargin(),
        )
        assertEquals("azzurra", dto.network)
        assertEquals(1, dto.messages.size)
        assertEquals(1725L, dto.messages[0].serverTime)
        assertEquals("#italia", dto.messages[0].channel)
    }
}
