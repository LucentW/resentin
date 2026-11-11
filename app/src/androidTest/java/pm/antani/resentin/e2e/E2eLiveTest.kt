package pm.antani.resentin.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pm.antani.resentin.net.HttpClients
import pm.antani.resentin.net.rest.E2eApi

/**
 * Against a real grappa, skipped unless `-e e2eHost <host> -e e2eToken <bearer>` are given (a
 * throwaway visitor: this creates and then removes an E2E identity on that account).
 */
@RunWith(AndroidJUnit4::class)
class E2eLiveTest {
    @Test
    fun syncMakesAnIdentityThenKeepsItAndReplaceStartsOver() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("e2eHost")
        val token = args.getString("e2eToken")
        assumeTrue(host != null && token != null)
        requireVisitor(host!!, token!!)
        val api = HttpClients.retrofit(host, HttpClients.okHttpClient({ token })).create(E2eApi::class.java)
        val store = E2eAccountStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val identity = E2eIdentity(store) { api }
        val slot = "visitor:live:1"
        val slug = "azzurra"
        store.delete(slot)
        runCatching { api.clearKeys(slug) }

        assertEquals(IdentityState.Ready, identity.sync(slot, slug))
        val list = api.getList(slug).body()!!
        var did = ""
        store.withAccount(slot) { did = it.own!!.did; assertEquals(it.own!!.master, list.master) }
        assertEquals(listOf(did), api.listDevices(slug).devices.map { it.did })
        val keys = api.getKeys(slug, did)
        assertTrue("one-time keys uploaded: $keys", keys.oneTimeKeys > 0 && keys.fallback)

        // Second sync keeps it: no new device, same list version.
        assertEquals(IdentityState.Ready, identity.sync(slot, slug))
        assertEquals(1, api.listDevices(slug).devices.size)
        assertEquals(list.ver, api.getList(slug).body()!!.ver)

        // A wiped device finds the identity "elsewhere" and writes nothing.
        store.delete(slot)
        assertEquals(IdentityState.Elsewhere(ElsewhereReason.NO_KEYS), identity.sync(slot, slug))

        // Explicit replace starts over with a new master.
        identity.replaceIdentity(slot, slug)
        assertTrue(api.getList(slug).body()!!.master != list.master)

        api.clearKeys(slug)
        store.delete(slot)
    }
}
