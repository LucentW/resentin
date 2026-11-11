package pm.antani.resentin.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.vodozemac_android.Account
import uniffi.vodozemac_android.verifyEd25519

/** On a device: the Rust library loads through JNA, and the Keystore-sealed store round-trips. */
@RunWith(AndroidJUnit4::class)
class E2eNativeTest {
    @Test
    fun olmRoundTripThroughTheNativeLibrary() {
        val alice = Account()
        val bob = Account()
        bob.generateOneTimeKeys(1uL)
        val session = alice.createOutboundSession(bob.curve25519Key(), bob.oneTimeKeys().first())
        val enc = session.encrypt("ciao".toByteArray())
        assertEquals(0uL, enc.messageType)
        val inbound = bob.createInboundSession(alice.curve25519Key(), enc.body)
        assertEquals("ciao", String(inbound.plaintext))
        assertTrue(verifyEd25519(alice.ed25519Key(), "x", alice.sign("x")))
    }

    @Test
    fun storeKeepsTheAccountAcrossReopenAndDropsAFailedBlock() = runBlocking {
        val store = E2eAccountStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val slot = "test:abc:1"
        store.delete(slot)
        var key = ""
        store.withAccount(slot) { key = it.account.ed25519Key() }
        store.withAccount(slot) { assertEquals(key, it.account.ed25519Key()) }
        // A block that throws stores nothing: the slot keeps what it held.
        runCatching { store.withAccount(slot) { it.master = Account(); error("boom") } }
        store.withAccount(slot) { assertNull(it.master) }
        store.delete(slot)
    }
}
