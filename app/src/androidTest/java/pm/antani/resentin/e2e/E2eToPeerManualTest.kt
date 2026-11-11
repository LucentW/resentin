package pm.antani.resentin.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pm.antani.resentin.net.HttpClients
import pm.antani.resentin.net.rest.E2eApi

/** Manual interop probe: writes an encrypted text to a real person's cicchetto and waits for a reply. */
@RunWith(AndroidJUnit4::class)
class E2eToPeerManualTest {
    @Test
    fun writeToPeer() = runBlocking {
        val a = InstrumentationRegistry.getArguments()
        val host = a.getString("e2eHost"); val token = a.getString("e2eToken"); val peer = a.getString("e2ePeer")
        assumeTrue(host != null && token != null && peer != null)
        val api = HttpClients.retrofit(host!!, HttpClients.okHttpClient({ token })).create(E2eApi::class.java)
        val store = E2eAccountStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val slot = "visitor:peer:1"; val slug = "azzurra"
        // NO clearKeys / store.delete here: a new master on every run is a key alarm for the peer.
        requireVisitor(host!!, token!!)
        val synced = E2eIdentity(store) { api }.sync(slot, slug)
        android.util.Log.i("E2ePeer", "sync: $synced")
        val c = E2eClient(store, { api }, slot, slug, ownNick = { a.getString("e2eNick")!! })
        val log = { m: String -> android.util.Log.i("E2ePeer", m) }
        // 1. Ask for consent (§4i): this is what shows up in cicchetto for the person to accept.
        log("asked: " + c.askToChat(peer!!))
        // 2. Wait for the answer.
        var answer: pm.antani.resentin.net.dto.E2eRequestDto? = null
        val askEnd = System.currentTimeMillis() + 900_000
        while (answer == null && System.currentTimeMillis() < askEnd) {
            answer = c.requests().firstOrNull { it.peer.equals(peer, true) && (it.state == "accepted" || it.state == "refused") }
            if (answer == null) delay(4_000)
        }
        log("answer: $answer")
        if (answer?.state != "accepted") return@runBlocking
        c.dismissRequest(answer.id)
        // 3. Keys and the text.
        log("send: " + c.sendText(peer, "Ciao Lucy! Questo e un messaggio cifrato E2E mandato da Resentin (visitor RsnTest).", "t1"))
        val end = System.currentTimeMillis() + 240_000
        while (System.currentTimeMillis() < end) {
            val ev = c.drain()
            if (ev.isNotEmpty()) log("events: $ev")
            if (ev.any { it is E2eEvent.Received }) break
            delay(4_000)
        }
    }
}
