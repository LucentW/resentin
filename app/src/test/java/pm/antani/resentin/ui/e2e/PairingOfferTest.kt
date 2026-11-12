package pm.antani.resentin.ui.e2e

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pm.antani.resentin.domain.events.WsEvent
import pm.antani.resentin.domain.events.WsEventDecoder
import pm.antani.resentin.e2e.PairCode

class PairingOfferTest {
    private fun decode(json: String) = WsEventDecoder.decode(Json.parseToJsonElement(json).jsonObject, "grappa:user:visitor:x")

    // The three frames of CLIENT_PROTOCOL §4g, as grappa pushes them on the user topic.
    @Test
    fun theOfferAndItsEndAreDecoded() {
        val offered = decode("""{"kind":"e2e_pairing_offered","rendezvous_id":"abc","curve":"K","expires_at":"2026-11-12T10:00:00Z"}""")
        assertEquals(WsEvent.E2ePairing("e2e_pairing_offered", "abc", curve = "K", expiresAt = "2026-11-12T10:00:00Z"), offered)
        val joined = decode("""{"kind":"e2e_pairing_message","rendezvous_id":"abc","seq":1,"from":"joiner"}""")
        assertEquals(WsEvent.E2ePairing("e2e_pairing_message", "abc", from = "joiner"), joined)
        val closed = decode("""{"kind":"e2e_pairing_closed","rendezvous_id":"abc","reason":"expired"}""")
        assertEquals(WsEvent.E2ePairing("e2e_pairing_closed", "abc", reason = "expired"), closed)
    }

    @Test
    fun anOfferIsShownOnlyWhereItIsOfUse() {
        val offer = PairCode("abc", "K")
        assertTrue(pairingOfferUseful(offer, listOf("azzurra")))
        assertFalse("this device already holds the identity everywhere", pairingOfferUseful(offer, emptyList()))
        assertFalse("no offer", pairingOfferUseful(null, listOf("azzurra")))
    }
}
