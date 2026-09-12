package pm.antani.resentin.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import pm.antani.resentin.data.prefs.channelKey
import pm.antani.resentin.net.dto.FeaturedChannelDto

class FeaturedChannelsHomeTest {
    @Test
    fun hidesChannelImmediatelyAfterSuccessfulFeaturedJoin() {
        val featured = listOf(
            FeaturedChannelDto("#grappa", "Main chat"),
            FeaturedChannelDto("#help", "Get help"),
        )

        val visible = filterVisibleFeaturedChannels(
            networkSlug = "azzurra",
            featuredChannels = featured,
            joinedChannelNames = emptySet(),
            dismissedChannelKeys = emptySet(),
            optimisticallyJoinedChannelKeys = setOf(channelKey("azzurra", "#GRAPPA")),
        )

        assertEquals(listOf(FeaturedChannelDto("#help", "Get help")), visible)
    }

    @Test
    fun hidesJoinedAndDismissedChannelsButKeepsOtherSuggestions() {
        val featured = listOf(
            FeaturedChannelDto("#grappa", "Main chat"),
            FeaturedChannelDto("#quiet", "A quieter place"),
            FeaturedChannelDto("#help", "Get help"),
        )

        val visible = filterVisibleFeaturedChannels(
            networkSlug = "azzurra",
            featuredChannels = featured,
            joinedChannelNames = setOf("#GRAPPA"),
            dismissedChannelKeys = setOf(channelKey("azzurra", "#quiet")),
        )

        assertEquals(listOf(FeaturedChannelDto("#help", "Get help")), visible)
    }
}