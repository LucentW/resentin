package pm.antani.resentin.irc

import org.junit.Assert.assertEquals
import org.junit.Test
import pm.antani.resentin.ui.common.withoutDccOverlaps

class DccFileLinkDetectorTest {

    @Test
    fun `finds a quoted delivered file with extension`() {
        val text = "📥 \"photo.jpg\" — https://irc.example.com/dcc_files/abcdefghijklmnopqrstuvwxyz.jpg"
        val links = DccFileLinkDetector.find(text)
        assertEquals(1, links.size)
        assertEquals("https://irc.example.com/dcc_files/abcdefghijklmnopqrstuvwxyz.jpg", links[0].path)
        assertEquals("photo.jpg", links[0].filename)
        assertEquals(
            "https://irc.example.com/dcc_files/abcdefghijklmnopqrstuvwxyz.jpg",
            text.substring(links[0].pathRange.first, links[0].pathRange.last + 1),
        )
    }

    @Test
    fun `finds a delivered file without extension`() {
        val text = "📥 \"notes\" — https://irc.example.com/dcc_files/234567abcdefghijklmnopqrst"
        val links = DccFileLinkDetector.find(text)
        assertEquals(1, links.size)
        assertEquals("https://irc.example.com/dcc_files/234567abcdefghijklmnopqrst", links[0].path)
        assertEquals("notes", links[0].filename)
    }

    @Test
    fun `finds the unnamed sentinel without a filename`() {
        val text = "📥 (unnamed) — https://irc.example.com/dcc_files/abcdefghijklmnopqrstuvwxyz"
        val links = DccFileLinkDetector.find(text)
        assertEquals(1, links.size)
        assertEquals("https://irc.example.com/dcc_files/abcdefghijklmnopqrstuvwxyz", links[0].path)
        assertEquals(null, links[0].filename)
    }

    @Test
    fun `a failure or refusal row has no path and matches nothing`() {
        val text = "someone's file \"photo.jpg\" did not arrive: the connection was refused"
        assertEquals(emptyList<DccFileLink>(), DccFileLinkDetector.find(text))
    }

    @Test
    fun `the retired relative shape no longer matches`() {
        // Pre-2127 `/networks/<id>/dcc_files/<slug>` route is gone server-side;
        // new-server-only means such a row stays plain text on purpose.
        val text = "📥 \"photo.jpg\" — /networks/3/dcc_files/abc234xyz"
        assertEquals(emptyList<DccFileLink>(), DccFileLinkDetector.find(text))
    }

    @Test
    fun `plain text has no match`() {
        assertEquals(emptyList<DccFileLink>(), DccFileLinkDetector.find("ciao a tutti"))
    }

    @Test
    fun `a generic url overlapping a dcc link is dropped`() {
        val dcc = 10..60
        assertEquals(emptyList<IntRange>(), withoutDccOverlaps(listOf(10..60), listOf(dcc)))
        assertEquals(emptyList<IntRange>(), withoutDccOverlaps(listOf(0..20), listOf(dcc)))
        assertEquals(listOf(0..5), withoutDccOverlaps(listOf(0..5), listOf(dcc)))
    }
}
