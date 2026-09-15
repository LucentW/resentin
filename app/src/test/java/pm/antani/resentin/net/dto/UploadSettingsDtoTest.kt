package pm.antani.resentin.net.dto

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pm.antani.resentin.net.AppJson

class UploadSettingsDtoTest {
    @Test
    fun `ttl preference decodes int and null`() {
        assertEquals(
            3600,
            AppJson.decodeFromString<UploadTtlSecondsDto>("""{"upload_ttl_seconds": 3600}""").uploadTtlSeconds,
        )
        assertNull(
            AppJson.decodeFromString<UploadTtlSecondsDto>("""{"upload_ttl_seconds": null}""").uploadTtlSeconds,
        )
        assertNull(AppJson.decodeFromString<UploadTtlSecondsDto>("""{}""").uploadTtlSeconds)
    }

    @Test
    fun `confirm opt-in decodes with false default`() {
        assertTrue(
            AppJson.decodeFromString<UploadConfirmEnabledDto>("""{"upload_confirm_enabled": true}""").uploadConfirmEnabled,
        )
        assertFalse(AppJson.decodeFromString<UploadConfirmEnabledDto>("""{}""").uploadConfirmEnabled)
    }

    @Test
    fun `confirm PUT body carries snake_case key`() {
        val body = AppJson.decodeFromString<JsonObject>(
            AppJson.encodeToString(UploadConfirmEnabledDto.serializer(), UploadConfirmEnabledDto(true)),
        )
        assertTrue(body["upload_confirm_enabled"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `ttl clear needs a hand-built body because explicit nulls are dropped`() {
        // AppJson ha explicitNulls = false: una data class con null perderebbe la
        // chiave e il "torna al default sito" diventerebbe un no-op — per questo il
        // PUT di clear passa per JsonObject (stessa trappola di autoAwayDebounce).
        val dropped = AppJson.decodeFromString<JsonObject>(
            AppJson.encodeToString(UploadTtlSecondsDto.serializer(), UploadTtlSecondsDto(null)),
        )
        assertFalse(dropped.containsKey("upload_ttl_seconds"))

        val explicit: JsonObject = buildJsonObject {
            put("upload_ttl_seconds", JsonNull)
        }
        assertTrue(explicit["upload_ttl_seconds"] is JsonNull)

        val valued: JsonObject = buildJsonObject {
            put("upload_ttl_seconds", JsonPrimitive(86400))
        }
        assertEquals(86400, valued["upload_ttl_seconds"]!!.jsonPrimitive.int)
    }

    @Test
    fun `ladder matches the server contract`() {
        assertEquals(listOf(3600, 43200, 86400, 259200), UPLOAD_TTL_LADDER_SECONDS)
        assertEquals(86400, UPLOAD_TTL_DEFAULT_SECONDS)
    }

    @Test
    fun `effective default prefers a usable stored preference`() {
        assertEquals(3600, effectiveUploadTtlSeconds(3600))
        // Fuori ladder (il server accetta fino a 1 anno come preferenza): il picker
        // non saprebbe mostrarlo, quindi si ricade sul default — come cicchetto.
        assertEquals(UPLOAD_TTL_DEFAULT_SECONDS, effectiveUploadTtlSeconds(123))
        assertEquals(UPLOAD_TTL_DEFAULT_SECONDS, effectiveUploadTtlSeconds(null))
    }
}
