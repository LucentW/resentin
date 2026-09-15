package pm.antani.resentin.net.dto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pm.antani.resentin.net.AppJson

class DisplayPrefsDtoTest {
    @Test
    fun legacyPayloadWithoutNewKeysDecodesToServerDefaults() {
        // Row scritta prima di #2029/#1766/#2037 (o da un client vecchio): i campi
        // assenti devono prendere i default del server, non azzerare a caso —
        // in particolare show_bottom_bar resta attivo (opt-out).
        val prefs = AppJson.decodeFromString<DisplayPrefsDto>(
            """{"colored_nicklist": true, "time_format": "hm", "presence_filter": {}}""",
        )

        assertTrue(prefs.coloredNicklist)
        assertEquals("hm", prefs.timeFormat)
        assertFalse(prefs.stripFormatting)
        assertTrue(prefs.showBottomBar)
        assertFalse(prefs.showEventBadge)
    }

    @Test
    fun fullPayloadRoundTripsEveryField() {
        val prefs = AppJson.decodeFromString<DisplayPrefsDto>(
            """
            {
              "colored_nicklist": true,
              "time_format": "hm",
              "presence_filter": {"libera #grappa": "hide"},
              "strip_formatting": true,
              "show_bottom_bar": false,
              "show_event_badge": true
            }
            """.trimIndent(),
        )

        assertTrue(prefs.coloredNicklist)
        assertEquals("hm", prefs.timeFormat)
        assertEquals(mapOf("libera #grappa" to "hide"), prefs.presenceFilter)
        assertTrue(prefs.stripFormatting)
        assertFalse(prefs.showBottomBar)
        assertTrue(prefs.showEventBadge)
    }

    @Test
    fun encodeEmitsSnakeCaseKeysForTheFullMapPut() {
        // Il PUT è full-map: se una chiave mancasse, il server la resetterebbe al
        // default e un toggle da questo client cancellerebbe una scelta di cicchetto.
        val body = AppJson.decodeFromString<JsonObject>(
            AppJson.encodeToString(
                DisplayPrefsDto.serializer(),
                DisplayPrefsDto(stripFormatting = true, showBottomBar = false, showEventBadge = true),
            ),
        )

        assertTrue(body["strip_formatting"]!!.jsonPrimitive.boolean)
        assertFalse(body["show_bottom_bar"]!!.jsonPrimitive.boolean)
        assertTrue(body["show_event_badge"]!!.jsonPrimitive.boolean)
        assertFalse(body["colored_nicklist"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun togglingOneFieldPreservesEveryOtherField() {
        // Contratto anti-clobber del toggle: copy su un campo solo, il resto viaggia
        // intatto nel PUT (presence_filter inclusa).
        val previous = DisplayPrefsDto(
            coloredNicklist = true,
            timeFormat = "hm",
            presenceFilter = mapOf("libera #grappa" to "show"),
            showBottomBar = false,
            showEventBadge = true,
        )
        val updated = previous.copy(stripFormatting = !previous.stripFormatting)

        assertTrue(updated.stripFormatting)
        assertEquals(previous.coloredNicklist, updated.coloredNicklist)
        assertEquals(previous.timeFormat, updated.timeFormat)
        assertEquals(previous.presenceFilter, updated.presenceFilter)
        assertEquals(previous.showBottomBar, updated.showBottomBar)
        assertEquals(previous.showEventBadge, updated.showEventBadge)
    }
}
