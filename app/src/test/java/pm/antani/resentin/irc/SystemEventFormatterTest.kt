package pm.antani.resentin.irc

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemEventFormatterTest {

    private val noMeta = JsonObject(emptyMap())

    @Test
    fun `join renders a compact system line`() {
        val result = SystemEventFormatter.format("join", "vjt", null, noMeta)
        assertEquals(FormattedEvent.System("vjt è entrato nel canale"), result)
    }

    @Test
    fun `part without reason`() {
        val result = SystemEventFormatter.format("part", "vjt", null, noMeta)
        assertEquals(FormattedEvent.System("vjt ha lasciato il canale"), result)
    }

    @Test
    fun `part with reason includes it in parentheses`() {
        val result = SystemEventFormatter.format("part", "vjt", "brb", noMeta)
        assertEquals(FormattedEvent.System("vjt ha lasciato il canale (brb)"), result)
    }

    @Test
    fun `quit with reason`() {
        val result = SystemEventFormatter.format("quit", "Guest1", "Ping timeout", noMeta)
        assertEquals(FormattedEvent.System("Guest1 ha abbandonato IRC (Ping timeout)"), result)
    }

    @Test
    fun `kick includes target and reason`() {
        val meta = JsonObject(mapOf("target" to JsonPrimitive("Fox")))
        val result = SystemEventFormatter.format("kick", "saBOTage", "no file sharing", meta)
        assertEquals(FormattedEvent.System("saBOTage ha espulso Fox (no file sharing)"), result)
    }

    @Test
    fun `mode includes modes and args`() {
        val meta = JsonObject(
            mapOf(
                "modes" to JsonPrimitive("+v"),
                "args" to buildJsonArray { add(JsonPrimitive("DreamsProgrammer")) },
            ),
        )
        val result = SystemEventFormatter.format("mode", "ChanServ", null, meta)
        assertEquals(FormattedEvent.System("ChanServ ha impostato modalità +v DreamsProgrammer"), result)
    }

    @Test
    fun `mode without args`() {
        val meta = JsonObject(mapOf("modes" to JsonPrimitive("+n")))
        val result = SystemEventFormatter.format("mode", "vjt", null, meta)
        assertEquals(FormattedEvent.System("vjt ha impostato modalità +n"), result)
    }

    @Test
    fun `nick_change reports the new nick`() {
        val meta = JsonObject(mapOf("new_nick" to JsonPrimitive("Guest29636")))
        val result = SystemEventFormatter.format("nick_change", "DreamsProgrammer", null, meta)
        assertEquals(FormattedEvent.System("DreamsProgrammer è ora conosciuto come Guest29636"), result)
    }

    @Test
    fun `topic change`() {
        val result = SystemEventFormatter.format("topic", "vjt", "new topic text", noMeta)
        assertEquals(FormattedEvent.System("vjt ha cambiato il topic"), result)
    }

    @Test
    fun `action strips the CTCP envelope`() {
        val body = "${Char(1)}ACTION si sposta in laboratorio ${Char(1)}"
        val result = SystemEventFormatter.format("action", "Johnny", body, noMeta)
        assertEquals(FormattedEvent.Chat("si sposta in laboratorio", isAction = true, isNotice = false), result)
    }

    @Test
    fun `notice is a chat bubble flagged as notice`() {
        val result = SystemEventFormatter.format("notice", "NickServ", "you are now identified", noMeta)
        assertEquals(FormattedEvent.Chat("you are now identified", isAction = false, isNotice = true), result)
    }

    @Test
    fun `privmsg is a plain chat bubble`() {
        val result = SystemEventFormatter.format("privmsg", "vjt", "ciao", noMeta)
        assertEquals(FormattedEvent.Chat("ciao", isAction = false, isNotice = false), result)
    }

    @Test
    fun `unknown future kind falls back to a plain chat bubble`() {
        val result = SystemEventFormatter.format("some_future_kind", "vjt", "text", noMeta)
        assertEquals(FormattedEvent.Chat("text", isAction = false, isNotice = false), result)
    }
}
