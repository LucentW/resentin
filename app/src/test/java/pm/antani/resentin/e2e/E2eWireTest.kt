package pm.antani.resentin.e2e

import org.junit.Assert.assertEquals
import org.junit.Test

class E2eWireTest {
    // The two vectors CLIENT_PROTOCOL §4f gives for grappa's did derivation.
    @Test
    fun didMatchesProtocolVectors() {
        assertEquals("If4x36FUomE", didOf("11qYAYKxCrfVS/7TyWQHOg7hcvPapiMlrwIaaPcHURo"))
        assertEquals("_Uw_z3-OAsk", didOf("7l01yXGoarYtEU3nwHF01/CIo8/r8U0lJ01TnULT9IY"))
    }

    @Test
    fun signatureTextsAreTheWireOnes() {
        assertEquals("irc-e2e:v2:id:C:M", idSigText("C", "M"))
        assertEquals("irc-e2e:v2:list:3:a,b", listSigText(3, listOf("a", "b")))
        assertEquals("irc-e2e:v2:dev:1:E", certSigText(1, "E"))
    }
}
