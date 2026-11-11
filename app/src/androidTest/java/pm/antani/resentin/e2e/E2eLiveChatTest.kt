package pm.antani.resentin.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pm.antani.resentin.net.HttpClients
import pm.antani.resentin.net.rest.E2eApi

/**
 * Two throwaway visitors on a real grappa talking end to end over real IRC. Skipped unless
 * `-e e2eHost -e e2eToken -e e2eNick -e e2eToken2 -e e2eNick2` are given.
 */
@RunWith(AndroidJUnit4::class)
class E2eLiveChatTest {
    private suspend fun drainUntil(c: E2eClient, what: String, timeoutMs: Long = 120_000, pick: (E2eEvent) -> Boolean): E2eEvent {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            c.drain().firstOrNull(pick)?.let { return it }
            delay(3_000)
        }
        error("timed out waiting for $what")
    }

    @Test
    fun twoVisitorsExchangeEncryptedText() = runBlocking {
        val a = InstrumentationRegistry.getArguments()
        val host = a.getString("e2eHost")
        assumeTrue(host != null && a.getString("e2eToken") != null && a.getString("e2eToken2") != null)
        requireVisitor(host!!, a.getString("e2eToken")!!)
        requireVisitor(host, a.getString("e2eToken2")!!)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val store = E2eAccountStore(ctx)
        fun api(token: String) = HttpClients.retrofit(host!!, HttpClients.okHttpClient({ token })).create(E2eApi::class.java)
        val apiA = api(a.getString("e2eToken")!!)
        val apiB = api(a.getString("e2eToken2")!!)
        val nickA = a.getString("e2eNick")!!
        val nickB = a.getString("e2eNick2")!!
        val slug = "azzurra"
        val slotA = "visitor:a:1"
        val slotB = "visitor:b:1"
        for (s in listOf(slotA, slotB)) store.delete(s)
        runCatching { apiA.clearKeys(slug) }
        runCatching { apiB.clearKeys(slug) }
        try {
            assertEquals(IdentityState.Ready, E2eIdentity(store) { apiA }.sync(slotA, slug))
            assertEquals(IdentityState.Ready, E2eIdentity(store) { apiB }.sync(slotB, slug))
            val ca = E2eClient(store, { apiA }, slotA, slug) { nickA }
            val cb = E2eClient(store, { apiB }, slotB, slug) { nickB }

            // A writes to B: KEYREQ, KEYS, PKMSG, all through grappa and the IRC server.
            ca.sendText(nickB, "ciao da Resentin", "t1")
            val keysDone = launchDrain(ca)
            val got = drainUntil(cb, "B to read the text") { it is E2eEvent.Received } as E2eEvent.Received
            keysDone.cancel()
            assertEquals("ciao da Resentin", got.text())

            // B answers on the session the PKMSG opened.
            cb.sendText(nickA, "ricevuto", "t2")
            val back = drainUntil(ca, "A to read the answer") { it is E2eEvent.Received } as E2eEvent.Received
            assertEquals("ricevuto", back.text())
        } finally {
            runCatching { apiA.clearKeys(slug) }
            runCatching { apiB.clearKeys(slug) }
            for (s in listOf(slotA, slotB)) store.delete(s)
        }
    }

    // A needs to keep draining so the KEYS B's grappa sends is read and the queued text goes out.
    private fun kotlinx.coroutines.CoroutineScope.launchDrain(c: E2eClient) = launch {
        while (true) { runCatching { c.drain() }; delay(3_000) }
    }
}
