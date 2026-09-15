package pm.antani.resentin.irc

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelReferenceDetectorTest {

    @Test
    fun findsChannelsAndTrimsSentencePunctuation() {
        val text = "scrivi in #Canale, oppure prova (#grappa)."
        val references = ChannelReferenceDetector.find(text)

        assertEquals(listOf("#Canale", "#grappa"), references.map { it.channelName })
        assertEquals(
            references.map { it.channelName },
            references.map { text.substring(it.range) },
        )
    }

    @Test
    fun ignoresHashtagsEmbeddedInWordsAndChannelLessHash() {
        assertEquals(emptyList<ChannelReference>(), ChannelReferenceDetector.find("parola#noncanale e #"))
    }

    @Test
    fun ignoresUrlFragmentsButFindsFollowingChannelReferences() {
        val text = "https://example.com/#noncanale poi #canale"
        assertEquals(listOf("#canale"), ChannelReferenceDetector.find(text).map { it.channelName })
    }

    @Test
    fun findsMultipleChannelReferences() {
        val text = "#uno #due"
        assertEquals(listOf("#uno", "#due"), ChannelReferenceDetector.find(text).map { it.channelName })
    }

    @Test
    fun onlyReturnsChannelReferencesFullyBeforeVisibleEnd() {
        val text = "topic #visibile #nascosto"
        val hiddenStart = text.indexOf("#nascosto")
        val partialVisibleEnd = text.indexOf("#visibile") + 3

        assertEquals(
            listOf("#visibile"),
            ChannelReferenceDetector.find(text, hiddenStart).map { it.channelName },
        )
        assertEquals(
            emptyList<ChannelReference>(),
            ChannelReferenceDetector.find(text, partialVisibleEnd),
        )
    }
}
