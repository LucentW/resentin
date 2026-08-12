package pm.antani.resentin.irc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelModesTest {

    @Test
    fun `no modes returns null`() {
        assertNull(formatChannelModes(emptyList(), emptyMap()))
    }

    @Test
    fun `plain modes with no params`() {
        assertEquals("+rnt", formatChannelModes(listOf("r", "n", "t"), emptyMap()))
    }

    @Test
    fun `a parameterized mode appends its value`() {
        assertEquals("+lnt 50", formatChannelModes(listOf("l", "n", "t"), mapOf("l" to "50")))
    }

    @Test
    fun `multiple parameterized modes append in mode order`() {
        assertEquals("+kl secret 50", formatChannelModes(listOf("k", "l"), mapOf("k" to "secret", "l" to "50")))
    }

    @Test
    fun `a mode with a null param value is treated as unparameterized`() {
        assertEquals("+r", formatChannelModes(listOf("r"), mapOf("r" to null)))
    }
}
