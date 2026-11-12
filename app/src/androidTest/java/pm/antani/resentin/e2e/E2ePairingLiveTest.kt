package pm.antani.resentin.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pm.antani.resentin.AppApplication
import pm.antani.resentin.net.AppJson
import pm.antani.resentin.net.HttpClients
import pm.antani.resentin.net.rest.E2eApi
import pm.antani.resentin.net.rest.E2ePairingApi

/**
 * Pairs a second device to the identity the logged-in app holds, against a real grappa. The app (its
 * real repository) is the HOLDER; a second device with its own store is the JOINER, on a second
 * session of the same account (a share token). The typed code is submitted by the test. Skipped
 * unless `-e e2eHost -e e2eToken` name the app's own session. Cleans up: revokes the added device.
 */
@RunWith(AndroidJUnit4::class)
class E2ePairingLiveTest {
    private suspend fun <T> until(what: String, timeoutMs: Long = 60_000, f: suspend () -> T?): T {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { f()?.let { android.util.Log.i("PairTest", "ok: $what"); return it }; delay(500) }
        error("timed out waiting for: $what")
    }

    /** Everything the second device needs, once it is paired to the app's identity. */
    private class Paired(val api2: E2eApi, val storeJ: E2eAccountStore, val slotJ: String, val joinerEd: String, val pairing2: E2ePairingApi, val rendezvous: String)

    /** Pairs a second device (its own store, a second session of the account) to the identity the app holds. */
    private suspend fun pairSecondDevice(host: String, token: String, slug: String, repo: pm.antani.resentin.domain.repository.E2eRepository): Paired {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val http = OkHttpClient()
        val json = "application/json".toMediaType()
        val minted = http.newCall(Request.Builder().url("https://$host/me/share-token").header("Authorization", "Bearer $token").post("".toRequestBody(json)).build()).execute().use { it.body!!.string() }
        val shareToken = (AppJson.parseToJsonElement(minted) as kotlinx.serialization.json.JsonObject)["token"]!!.toString().trim('"')
        val consumed = http.newCall(Request.Builder().url("https://$host/auth/share/consume").post("""{"token":"$shareToken"}""".toRequestBody(json)).build()).execute().use { it.body!!.string() }
        val bearer2 = (AppJson.parseToJsonElement(consumed) as kotlinx.serialization.json.JsonObject)["token"]!!.toString().trim('"')
        val retrofit2 = HttpClients.retrofit(host, HttpClients.okHttpClient({ bearer2 }))
        val api2 = retrofit2.create(E2eApi::class.java)
        val pairing2 = retrofit2.create(E2ePairingApi::class.java)
        val storeJ = E2eAccountStore(ctx)
        val slotJ = "pairtest:joiner:1"
        storeJ.delete(slotJ)
        val identityJ = E2eIdentity(storeJ) { api2 }
        val scope = CoroutineScope(Dispatchers.IO)
        val holder = repo.startHolder().getOrThrow()
        val offer = until("the offer") { pairing2.offer().offer }
        val joiner = JoinerPairing(identityJ, { listOf(slug to slotJ) }, PairCode(offer.rendezvousId, offer.curve), scope)
        val r = pairing2.join(offer.rendezvousId).rendezvous
        val ch = PairingChannel(pairing2, r, { joiner.onPayload(it) }, { joiner.onClosed(it) })
        joiner.attach(ch)
        val poller = scope.launch { while (true) { runCatching { ch.catchUp() }; repo.catchUpPairings(); delay(700) } }
        val code = until("the joiner's check code") { joiner.state.value.checkCode }
        until("the holder to hear the hello") { holder.state.value.stage.takeIf { it == HolderStage.CODE } }
        holder.submitCode(code)
        until("the joiner to see the offer") { joiner.state.value.stage.takeIf { it == JoinerStage.CONFIRM } }
        joiner.confirm()
        until("the joiner to finish") { joiner.state.value.stage.takeIf { it == JoinerStage.DONE } }
        until("the holder to finish") { holder.state.value.stage.takeIf { it == HolderStage.DONE } }
        poller.cancel()
        return Paired(api2, storeJ, slotJ, storeJ.peek(slotJ) { it.account.ed25519Key() }, pairing2, offer.rendezvousId)
    }

    @Test
    fun aTextCopiedToTheSiblingShowsThereAndBack() = runBlocking<Unit> {
        val a = InstrumentationRegistry.getArguments()
        val host = a.getString("e2eHost"); val token = a.getString("e2eToken")
        assumeTrue(host != null && token != null)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (ctx.applicationContext as AppApplication).container
        val repo = container.e2eRepository
        val slug = "azzurra"
        val paired = pairSecondDevice(host!!, token!!, slug, repo)
        try {
            val dao = container.database.e2eMessageDao()
            val nick = "CopyTestNick"
            val slotApp = container.tokenStore.session.value!!.let { "${it.wsSubject}:${container.database.networkDao().idForSlug(slug)}" }
            val appClient = E2eClient(container.e2eAccountStore, { container.authRepository.api(E2eApi::class.java) }, slotApp, slug, ownNick = { "RsnTest3" })
            val joinerClient = E2eClient(paired.storeJ, { paired.api2 }, paired.slotJ, slug, ownNick = { "RsnTest3" })

            // The app sent a text to someone: the sibling is told through grappa (claim, copy), never IRC.
            appClient.copyToSiblings(nick, "mandato dall'app")
            val got = until("the joiner to read the copy") { joinerClient.drain().filterIsInstance<E2eEvent.Copied>().firstOrNull() }
            assertEquals(nick, got.peer); assertEquals("mandato dall'app", got.body)

            // And back, on the session the first copy opened: nothing to claim this time.
            joinerClient.copyToSiblings(nick, "mandato dall'altro device")
            val back = until("the app to read the copy") { appClient.drain().filterIsInstance<E2eEvent.Copied>().firstOrNull() }
            assertEquals("mandato dall'altro device", back.body)
        } finally {
            repo.revokeDevice(slug, paired.joinerEd)
            paired.storeJ.delete(paired.slotJ)
            runCatching { paired.pairing2.close(paired.rendezvous) }
        }
    }

    @Test
    fun appHoldsAndASecondDeviceJoins() = runBlocking<Unit> {
        val a = InstrumentationRegistry.getArguments()
        val host = a.getString("e2eHost"); val token = a.getString("e2eToken")
        assumeTrue(host != null && token != null)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val container = (ctx.applicationContext as AppApplication).container
        val repo = container.e2eRepository
        val slug = "azzurra"

        // A second session of the same account: mint a share token with the app's bearer and consume it.
        val http = OkHttpClient()
        val json = "application/json".toMediaType()
        val minted = http.newCall(Request.Builder().url("https://$host/me/share-token").header("Authorization", "Bearer $token").post("".toRequestBody(json)).build()).execute().use { it.body!!.string() }
        val shareToken = AppJson.parseToJsonElement(minted).let { (it as kotlinx.serialization.json.JsonObject)["token"]!!.toString().trim('"') }
        val consumed = http.newCall(Request.Builder().url("https://$host/auth/share/consume").post("""{"token":"$shareToken"}""".toRequestBody(json)).build()).execute().use { it.body!!.string() }
        val bearer2 = (AppJson.parseToJsonElement(consumed) as kotlinx.serialization.json.JsonObject)["token"]!!.toString().trim('"')
        assertTrue("second session", bearer2.isNotEmpty() && bearer2 != token)

        val retrofit2 = HttpClients.retrofit(host!!, HttpClients.okHttpClient({ bearer2 }))
        val api2 = retrofit2.create(E2eApi::class.java)
        val pairing2 = retrofit2.create(E2ePairingApi::class.java)
        val storeJ = E2eAccountStore(ctx)
        val slotJ = "pairtest:joiner:1"
        storeJ.delete(slotJ)
        val identityJ = E2eIdentity(storeJ) { api2 }
        val scope = CoroutineScope(Dispatchers.IO)

        // The app waits for the join; the version of the list now is what we compare against.
        val before = repo.ownKeyInfo(slug)!!
        val holder = repo.startHolder().getOrThrow()

        val offer = until("the offer") { pairing2.offer().offer }
        assertEquals(holder.curve, offer.curve)

        val joiner = JoinerPairing(identityJ, { listOf(slug to slotJ) }, PairCode(offer.rendezvousId, offer.curve), scope)
        val r = pairing2.join(offer.rendezvousId).rendezvous
        val ch = PairingChannel(pairing2, r, { joiner.onPayload(it) }, { joiner.onClosed(it) })
        joiner.attach(ch)
        // The app is not in the foreground here, so its socket is closed and no push rings: poll both sides.
        val poller = scope.launch { while (true) { runCatching { ch.catchUp() }; repo.catchUpPairings(); delay(700) } }

        val code = until("the joiner's check code") { joiner.state.value.checkCode }
        until("the holder to hear the hello") { holder.state.value.stage.takeIf { it == HolderStage.CODE } }
        holder.submitCode(code)            // the user typing the code on the holder

        until("the joiner to see the offer") { joiner.state.value.stage.takeIf { it == JoinerStage.CONFIRM } }
        assertEquals(listOf(slug), joiner.state.value.offered)
        joiner.confirm()                   // the user tapping "Pair this device"

        until("the joiner to finish") { joiner.state.value.stage.takeIf { it == JoinerStage.DONE } }
        until("the holder to finish") { holder.state.value.stage.takeIf { it == HolderStage.DONE } }
        assertEquals(listOf(NetworkResult(slug, "added")), joiner.state.value.results)
        assertEquals(listOf(NetworkResult(slug, "added")), holder.state.value.results)
        poller.cancel()

        // The identity now lists two devices at a higher version, and the joiner holds it without the master.
        val after = repo.ownKeyInfo(slug)!!
        assertEquals(before.devices + 1, after.devices)
        assertEquals(before.listVersion + 1, after.listVersion)
        assertEquals(before.master, after.master)
        val j = storeJ.peek(slotJ) { it.own!! }
        assertEquals(before.master, j.master)
        assertEquals(after.listVersion, j.list.ver)
        assertEquals(null, storeJ.peek(slotJ) { it.master })

        // Clean up: revoke the joiner's device (the holder holds the master).
        val ed = storeJ.peek(slotJ) { it.account.ed25519Key() }
        assertEquals("revoked", repo.revokeDevice(slug, ed))
        assertEquals(before.devices, repo.ownKeyInfo(slug)!!.devices)
        storeJ.delete(slotJ)
        runCatching { pairing2.close(offer.rendezvousId) }
    }
}
