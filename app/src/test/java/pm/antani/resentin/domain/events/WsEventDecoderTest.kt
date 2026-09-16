package pm.antani.resentin.domain.events

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WsEventDecoderTest {
    @Test
    fun `decodes high priority user topic events`() {
        val channels = WsEventDecoder.decode(
            buildJsonObject { put("kind", "channels_changed") },
            "grappa:user:test",
        )
        assertTrue(channels is WsEvent.ChannelsChanged)

        val away = WsEventDecoder.decode(
            buildJsonObject {
                put("kind", "peer_away")
                put("network", "azzurra")
                put("peer", "Alice")
                put("message", "back later")
            },
            "grappa:user:test",
        ) as WsEvent.PeerAway
        assertEquals("azzurra", away.away.network)
        assertEquals("Alice", away.away.peer)

        val failed = WsEventDecoder.decode(
            buildJsonObject {
                put("kind", "join_failed")
                put("network", "azzurra")
                put("channel", "#ops")
                put("state", "failed")
                put("reason", "invite only")
                put("numeric", 473)
            },
            "grappa:user:test",
        ) as WsEvent.JoinFailed
        assertEquals(473, failed.failed.numeric)
        assertEquals("invite only", failed.failed.reason)

        val kicked = WsEventDecoder.decode(
            buildJsonObject {
                put("kind", "kicked")
                put("network", "azzurra")
                put("channel", "#ops")
                put("state", "kicked")
                put("by", "operator")
                put("reason", "flooding")
            },
            "grappa:user:test",
        ) as WsEvent.Kicked
        assertEquals("operator", kicked.kicked.by)

        val purged = WsEventDecoder.decode(
            buildJsonObject {
                put("kind", "archive_purged")
                put("network_slug", "azzurra")
                put("target", "#old")
            },
            "grappa:user:test",
        ) as WsEvent.ArchivePurged
        assertEquals("#old", purged.purged.target)
    }

    @Test
    fun `rejects window state with wrong state discriminator`() {
        val event = WsEventDecoder.decode(
            buildJsonObject {
                put("kind", "kicked")
                put("network", "azzurra")
                put("channel", "#ops")
                put("state", "joined")
            },
            "grappa:user:test",
        )
        assertTrue(event is WsEvent.Unknown)
    }
}
