package pm.antani.resentin.net.dto

import org.junit.Assert.assertEquals
import org.junit.Test
import pm.antani.resentin.net.AppJson

class MeDtoTest {
    @Test
    fun decodesUnreadCountsEnvelope() {
        val me = AppJson.decodeFromString<MeDto>(
            """
            {
              "kind": "visitor",
              "unread_counts": {
                "libera": {
                  "alice": {
                    "messages": 3,
                    "mentions": 1,
                    "events": 0,
                    "severity": "mention"
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val counts = me.unreadCounts["libera"]!!["alice"]!!
        assertEquals(3, counts.messages)
        assertEquals(1, counts.mentions)
        assertEquals("mention", counts.severity)
    }
    @Test
    fun decodesAvailableNetworksInHomeEnvelope() {
        val me = AppJson.decodeFromString<MeDto>(
            """
            {
              "kind": "visitor",
              "home_data": {
                "networks": [],
                "available_networks": [{"slug": "libera"}]
              }
            }
            """.trimIndent(),
        )

        assertEquals(listOf(AvailableNetworkDto("libera")), me.homeData?.availableNetworks)
    }
}
