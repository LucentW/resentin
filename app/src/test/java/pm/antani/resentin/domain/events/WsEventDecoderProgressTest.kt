package pm.antani.resentin.domain.events

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WsEventDecoderProgressTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes connection progress`() {
        val event = WsEventDecoder.decode(
            json.parseToJsonElement("""{"kind":"connection_progress","network":"libera","state":"connecting"}""").jsonObject,
            "grappa:user:alice",
        ) as WsEvent.ConnectionProgress

        assertEquals("libera", event.progress.network)
        assertEquals("connecting", event.progress.state)
    }

    @Test
    fun `decodes recover progress and result`() {
        val progress = WsEventDecoder.decode(
            json.parseToJsonElement("""{"kind":"recover_progress","network":"libera","step":"identify","status":"running","reason":null}""").jsonObject,
            "grappa:user:alice",
        ) as WsEvent.RecoverProgress
        val result = WsEventDecoder.decode(
            json.parseToJsonElement("""{"kind":"recover_result","network":"libera","outcome":"failed","reason":"wrong_password"}""").jsonObject,
            "grappa:user:alice",
        ) as WsEvent.RecoverResult

        assertEquals("identify", progress.progress.step)
        assertEquals("running", progress.progress.status)
        assertEquals("failed", result.result.outcome)
        assertEquals("wrong_password", result.result.reason)
    }

    @Test
    fun `malformed progress falls back to unknown`() {
        val event = WsEventDecoder.decode(
            json.parseToJsonElement("""{"kind":"connection_progress","network":"libera","state":"broken"}""").jsonObject,
            "grappa:user:alice",
        )

        assertTrue(event is WsEvent.Unknown)
    }
}
