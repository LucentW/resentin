package pm.antani.resentin.irc

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlDetectorTest {

    @Test
    fun `finds a bare url`() {
        val text = "vedi https://example.com/path per info"
        val ranges = UrlDetector.find(text)
        assertEquals(listOf("https://example.com/path"), ranges.map { text.substring(it) })
    }

    @Test
    fun `trims trailing sentence punctuation`() {
        val text = "vedi https://example.com/path."
        val ranges = UrlDetector.find(text)
        assertEquals(listOf("https://example.com/path"), ranges.map { text.substring(it) })
    }

    @Test
    fun `trims a wrapping closing parenthesis`() {
        val text = "(https://example.com/path)"
        val ranges = UrlDetector.find(text)
        assertEquals(listOf("https://example.com/path"), ranges.map { text.substring(it) })
    }

    @Test
    fun `finds multiple urls`() {
        val text = "https://a.example.com e anche https://b.example.com"
        val ranges = UrlDetector.find(text)
        assertEquals(listOf("https://a.example.com", "https://b.example.com"), ranges.map { text.substring(it) })
    }

    @Test
    fun `no urls returns empty list`() {
        assertEquals(emptyList<IntRange>(), UrlDetector.find("nessun link qui"))
    }

    @Test
    fun `http without s is also detected`() {
        val text = "http://insecure.example.com"
        val ranges = UrlDetector.find(text)
        assertEquals(listOf("http://insecure.example.com"), ranges.map { text.substring(it) })
    }
}
