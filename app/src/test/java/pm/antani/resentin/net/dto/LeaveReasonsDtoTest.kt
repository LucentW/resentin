package pm.antani.resentin.net.dto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pm.antani.resentin.net.AppJson

class LeaveReasonsDtoTest {
    @Test
    fun quitPartReasonDecodesSnakeCase() {
        val dto = AppJson.decodeFromString(
            QuitPartReasonDto.serializer(),
            """{"quit_part_reason":"alla prossima"}""",
        )
        assertEquals("alla prossima", dto.quitPartReason)
    }

    @Test
    fun nullMeansNoDefault() {
        val dto = AppJson.decodeFromString(
            QuitPartReasonDto.serializer(),
            """{"quit_part_reason":null}""",
        )
        assertNull(dto.quitPartReason)
        val auto = AppJson.decodeFromString(
            AutoAwayReasonDto.serializer(),
            """{"auto_away_reason":null}""",
        )
        assertNull(auto.autoAwayReason)
    }

    @Test
    fun autoAwayReasonDecodesSnakeCase() {
        val dto = AppJson.decodeFromString(
            AutoAwayReasonDto.serializer(),
            """{"auto_away_reason":"via al lavoro"}""",
        )
        assertEquals("via al lavoro", dto.autoAwayReason)
    }
}
