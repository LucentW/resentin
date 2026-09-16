package pm.antani.resentin.domain.events

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pm.antani.resentin.net.dto.LinksBundleDto
import pm.antani.resentin.net.dto.LinksEntryDto

class WsEventDecoderLinksTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesLinksBundleEntries() {
        val event = WsEventDecoder.decode(
            json.parseToJsonElement(
                """{"kind":"links_bundle","network":"libera","mask":"*.example","entries":[{"server":"irc-a","linked_to":"irc-b","hopcount":1,"description":"Main server"}]}""",
            ).jsonObject,
            topic = "grappa:user:1",
        )

        assertEquals(
            WsEvent.LinksBundle(
                LinksBundleDto(
                    network = "libera",
                    mask = "*.example",
                    entries = listOf(LinksEntryDto("irc-a", "irc-b", 1, "Main server")),
                ),
            ),
            event,
        )
    }

    @Test
    fun malformedLinksBundleFallsBackToUnknown() {
        val event = WsEventDecoder.decode(
            json.parseToJsonElement("{\"kind\":\"links_bundle\",\"network\":\"libera\",\"entries\":null}").jsonObject,
            topic = "grappa:user:1",
        )

        assertTrue(event is WsEvent.Unknown)
    }
}
