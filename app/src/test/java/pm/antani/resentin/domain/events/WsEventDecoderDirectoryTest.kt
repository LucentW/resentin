package pm.antani.resentin.domain.events

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WsEventDecoderDirectoryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesDirectoryProgress() {
        val event = WsEventDecoder.decode(
            json.parseToJsonElement("{\"kind\":\"directory_progress\",\"network\":\"libera\",\"count\":42}").jsonObject,
            topic = "grappa:user:1",
        )

        assertEquals(WsEvent.DirectoryProgress(pm.antani.resentin.net.dto.DirectoryProgressDto("libera", 42)), event)
    }

    @Test
    fun decodesDirectoryCompleteAndFailed() {
        val complete = WsEventDecoder.decode(
            json.parseToJsonElement("{\"kind\":\"directory_complete\",\"network\":\"libera\",\"total\":120}").jsonObject,
            topic = "grappa:user:1",
        )
        val failed = WsEventDecoder.decode(
            json.parseToJsonElement("{\"kind\":\"directory_failed\",\"network\":\"libera\",\"reason\":\"upstream timeout\"}").jsonObject,
            topic = "grappa:user:1",
        )

        assertEquals(WsEvent.DirectoryComplete(pm.antani.resentin.net.dto.DirectoryCompleteDto("libera", 120)), complete)
        assertEquals(WsEvent.DirectoryFailed(pm.antani.resentin.net.dto.DirectoryFailedDto("libera", "upstream timeout")), failed)
    }

    @Test
    fun rejectsNegativeDirectoryCountsAsUnknown() {
        val event = WsEventDecoder.decode(
            json.parseToJsonElement("{\"kind\":\"directory_progress\",\"network\":\"libera\",\"count\":-1}").jsonObject,
            topic = "grappa:user:1",
        )

        assertTrue(event is WsEvent.Unknown)
    }
}
