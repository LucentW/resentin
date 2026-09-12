package pm.antani.resentin.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationWizardTest {
    @Test
    fun azzurraTemplateBuildsSourceVerifiedVerbs() {
        val template = templateForFlavor("azzurra")!!
        assertEquals("NickServ", template.servicesNick)
        // Password FIRST, email second — the ordering is load-bearing.
        assertEquals("REGISTER s3cret me@example.com", template.buildRegister("s3cret", "me@example.com"))
        // Azzurra AUTH takes the code alone — the nick is NOT part of the verb.
        assertEquals("AUTH 123456", template.buildVerify("SomeNick", "123456"))
    }

    @Test
    fun onlyAzzurraIsRegisterable() {
        assertTrue(registerableFlavor("azzurra"))
        assertFalse(registerableFlavor("atheme"))
        assertFalse(registerableFlavor("oftc"))
        assertFalse(registerableFlavor("unknown"))
        assertFalse(registerableFlavor(null))
        assertNull(templateForFlavor("atheme"))
        assertNull(templateForFlavor(null))
    }

    @Test
    fun emailValidationIsAShapeCheckOnly() {
        assertTrue(isValidWizardEmail("me@example.com"))
        assertTrue(isValidWizardEmail("  me@example.com  "))
        assertFalse(isValidWizardEmail("not-an-email"))
        assertFalse(isValidWizardEmail("me@host"))
        assertFalse(isValidWizardEmail(""))
    }

    @Test
    fun passwordLengthBoundsMatchCicchetto() {
        assertEquals(5, WIZARD_MIN_PASSWORD)
        assertEquals(32, WIZARD_MAX_PASSWORD)
        assertFalse(isValidWizardPassword("1234"))
        assertTrue(isValidWizardPassword("12345"))
        assertTrue(isValidWizardPassword("a".repeat(32)))
        assertFalse(isValidWizardPassword("a".repeat(33)))
    }
}
