package pm.antani.resentin.irc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pm.antani.resentin.BuildConfig
import pm.antani.resentin.net.AppJson
import pm.antani.resentin.net.dto.IrcMessagesDto

class IrcMessagesTest {

    @Test
    fun defaultUsesAppVersionAndNeverLiteralPlaceholder() {
        val def = defaultIrcMessage()
        assertEquals("Grappa-IRC - Resentin ${BuildConfig.VERSION_NAME}", def)
        assertFalse(def.contains("%version"))
        // Il template grezzo contiene il placeholder, il risolto mai.
        assertTrue(IRC_MESSAGE_TEMPLATE.contains("%version"))
    }

    @Test
    fun storedNullFallsBackToDefaultForUpgraders() {
        // Chi aggiorna senza aver personalizzato: `null` salvato -> predefinito versionato.
        assertEquals(defaultIrcMessage(), resolveStoredIrcMessage(null))
    }

    @Test
    fun blankStoredKeepsNoReasonBehavior() {
        // Campo svuotato -> nessun motivo (comportamento attuale senza motivo).
        assertNull(resolveStoredIrcMessage(""))
        assertNull(resolveStoredIrcMessage("   "))
    }

    @Test
    fun customMessageIsPreservedAndPlaceholderExpanded() {
        assertEquals("ciao", resolveStoredIrcMessage("ciao"))
        assertEquals(
            "ciao ${BuildConfig.VERSION_NAME}",
            resolveStoredIrcMessage("ciao %version"),
        )
        val resolved = resolveStoredIrcMessage("Grappa-IRC - Resentin %version")!!
        assertFalse(resolved.contains("%version"))
        assertEquals(defaultIrcMessage(), resolved)
    }

    @Test
    fun explicitReasonWinsOverStored() {
        // `/part` / `/quit` con motivo esplicito: scelta esplicita preservata.
        assertEquals("via!", partReasonForSend(listOf("via!"), "custom"))
        assertEquals("bye!", quitReasonForSend("bye!", "custom"))
        // `%version` espanso anche nei motivi espliciti, mai letterale.
        assertEquals(
            "bye ${BuildConfig.VERSION_NAME}",
            quitReasonForSend("bye %version", "custom"),
        )
        assertFalse(quitReasonForSend("bye %version", "custom")!!.contains("%version"))
    }

    @Test
    fun missingExplicitReasonFallsBackToStored() {
        assertEquals("custom", partReasonForSend(emptyList(), "custom"))
        assertEquals(defaultIrcMessage(), partReasonForSend(emptyList(), null))
        assertNull(partReasonForSend(emptyList(), ""))
        assertEquals("custom", quitReasonForSend(null, "custom"))
        assertEquals(defaultIrcMessage(), quitReasonForSend(null, null))
        assertNull(quitReasonForSend(null, ""))
        // Blank esplicito = omesso (il ViewModel fa già `ifBlank { null }`): usa il salvato.
        assertEquals("custom", quitReasonForSend("   ", "custom"))
        assertEquals(defaultIrcMessage(), quitReasonForSend("   ", null))
    }

    @Test
    fun normalizeForSaveDistinguishesDefaultEmptyAndCustom() {
        // Blank -> "" (nessun motivo, distinto da `null` = predefinito).
        assertEquals("", normalizeIrcMessageForSave(""))
        assertEquals("", normalizeIrcMessageForSave("   "))
        // Uguale al predefinito/template -> `null` (così i futuri bump di versione
        // si propagano senza congelare la vecchia né sovrascrivere altri device).
        assertNull(normalizeIrcMessageForSave(defaultIrcMessage()))
        assertNull(normalizeIrcMessageForSave(IRC_MESSAGE_TEMPLATE))
        // Custom -> grezzo (mai versione risolta persistita).
        assertEquals("ciao", normalizeIrcMessageForSave("ciao"))
        assertEquals("ciao %version", normalizeIrcMessageForSave("ciao %version"))
    }

    @Test
    fun dtoRoundTripsSnakeCaseAndNulls() {
        val dto = AppJson.decodeFromString<IrcMessagesDto>(
            """{"part_message": "ciao", "quit_message": null}""",
        )
        assertEquals("ciao", dto.partMessage)
        assertNull(dto.quitMessage)

        // Mai personalizzato: chiavi assenti -> `null` -> predefiniti all'invio.
        val empty = AppJson.decodeFromString<IrcMessagesDto>("{}")
        assertNull(empty.partMessage)
        assertNull(empty.quitMessage)
        assertEquals(defaultIrcMessage(), resolveStoredIrcMessage(empty.partMessage))
        assertEquals(defaultIrcMessage(), resolveStoredIrcMessage(empty.quitMessage))
    }

    @Test
    fun dtoIgnoresUnknownKeysForForwardCompatibility() {
        val dto = AppJson.decodeFromString<IrcMessagesDto>(
            """{"part_message": "a", "quit_message": "b", "future_key": 1}""",
        )
        assertEquals("a", dto.partMessage)
        assertEquals("b", dto.quitMessage)
    }
}
