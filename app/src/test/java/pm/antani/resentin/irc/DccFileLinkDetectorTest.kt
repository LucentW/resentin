package pm.antani.resentin.irc

import org.junit.Assert.assertEquals
import org.junit.Test

class DccFileLinkDetectorTest {

    @Test
    fun `finds a quoted delivered file`() {
        val text = "📥 \"photo.jpg\" — /networks/3/dcc_files/abc234xyz"
        val links = DccFileLinkDetector.find(text)
        assertEquals(1, links.size)
        assertEquals("/networks/3/dcc_files/abc234xyz", links[0].path)
        assertEquals("photo.jpg", links[0].filename)
        assertEquals("/networks/3/dcc_files/abc234xyz", text.substring(links[0].pathRange.first, links[0].pathRange.last + 1))
    }

    @Test
    fun `finds the unnamed sentinel without a filename`() {
        val text = "📥 (unnamed) — /networks/12/dcc_files/qqzz2233"
        val links = DccFileLinkDetector.find(text)
        assertEquals(1, links.size)
        assertEquals("/networks/12/dcc_files/qqzz2233", links[0].path)
        assertEquals(null, links[0].filename)
    }

    @Test
    fun `a failure or refusal row has no path and matches nothing`() {
        val text = "someone's file \"photo.jpg\" did not arrive: the connection was refused"
        assertEquals(emptyList<DccFileLink>(), DccFileLinkDetector.find(text))
    }

    @Test
    fun `plain text has no match`() {
        assertEquals(emptyList<DccFileLink>(), DccFileLinkDetector.find("ciao a tutti"))
    }
}
