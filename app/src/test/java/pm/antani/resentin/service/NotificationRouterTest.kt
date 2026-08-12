package pm.antani.resentin.service

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pm.antani.resentin.domain.session.OpenChat
import pm.antani.resentin.net.dto.ScrollbackMessageDto

private fun message(
    channel: String,
    sender: String,
    body: String? = "hello",
    kind: String = "privmsg",
) = ScrollbackMessageDto(
    id = 1,
    network = "azzurra",
    channel = channel,
    serverTime = 0,
    kind = kind,
    sender = sender,
    body = body,
    meta = JsonObject(emptyMap()),
)

class NotificationRouterTest {

    @Test
    fun `a DM notifies even without mentioning our nick`() {
        val msg = message(channel = "lucy", sender = "Cavallopazzo", body = "ciao!")
        val bucket = queryBucket(msg, myNick = "Lucy")
        assertEquals("Cavallopazzo", bucket)
        assertTrue(shouldNotify(msg, openChat = null, myNick = "Lucy", bucket = bucket))
    }

    @Test
    fun `a channel message without a mention does not notify`() {
        val msg = message(channel = "#grappa", sender = "vjt", body = "ciao a tutti")
        val bucket = queryBucket(msg, myNick = "Lucy")
        assertFalse(shouldNotify(msg, openChat = null, myNick = "Lucy", bucket = bucket))
    }

    @Test
    fun `a channel message that mentions our nick notifies`() {
        val msg = message(channel = "#grappa", sender = "vjt", body = "ehi Lucy guarda qua")
        val bucket = queryBucket(msg, myNick = "Lucy")
        assertTrue(shouldNotify(msg, openChat = null, myNick = "Lucy", bucket = bucket))
    }

    @Test
    fun `our own message never notifies`() {
        val msg = message(channel = "vjt", sender = "Lucy", body = "ciao")
        val bucket = queryBucket(msg, myNick = "Lucy")
        assertFalse(shouldNotify(msg, openChat = null, myNick = "Lucy", bucket = bucket))
    }

    @Test
    fun `a DM for the chat currently open is suppressed`() {
        val msg = message(channel = "lucy", sender = "Cavallopazzo", body = "ciao!")
        val bucket = queryBucket(msg, myNick = "Lucy")
        val open = OpenChat(networkSlug = "azzurra", channelName = "Cavallopazzo")
        assertFalse(shouldNotify(msg, openChat = open, myNick = "Lucy", bucket = bucket))
    }

    @Test
    fun `a join event never notifies even if it happens to contain our nick`() {
        val msg = message(channel = "#grappa", sender = "Lucy2", body = null, kind = "join")
        val bucket = queryBucket(msg, myNick = "Lucy")
        assertFalse(shouldNotify(msg, openChat = null, myNick = "Lucy", bucket = bucket))
    }

    @Test
    fun `queryBucket normalizes both directions of a DM to the partner's nick`() {
        val outgoing = message(channel = "vjt", sender = "Lucy")
        val incoming = message(channel = "lucy", sender = "vjt")
        assertEquals("vjt", queryBucket(outgoing, myNick = "Lucy"))
        assertEquals("vjt", queryBucket(incoming, myNick = "Lucy"))
    }
}
