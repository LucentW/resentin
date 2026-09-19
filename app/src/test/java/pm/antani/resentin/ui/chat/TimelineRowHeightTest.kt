package pm.antani.resentin.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineRowHeightTest {
    private val bounds = TimelineRowHeightBounds(minPx = 20, maxPx = 160, stepPx = 8)

    @Test
    fun medianOddSamplePicksMiddle() {
        assertEquals(48, medianTimelineRowHeightPx(listOf(22, 48, 200)))
    }

    @Test
    fun medianEvenSampleAveragesLowerMiddlePair() {
        assertEquals(35, medianTimelineRowHeightPx(listOf(20, 30, 40, 200)))
    }

    @Test
    fun medianEmptySampleIsNull() {
        assertNull(medianTimelineRowHeightPx(emptyList()))
    }

    @Test
    fun firstSampleAdoptedOnLattice() {
        // 50 -> nearest 8dp lattice point 48.
        assertEquals(48, nextTimelineRowHeightPx(UNSAMPLED_ROW_HEIGHT_PX, 50, bounds))
    }

    @Test
    fun smallWobbleInsideStepKeepsStandingEstimate() {
        assertEquals(48, nextTimelineRowHeightPx(48, 52, bounds))
    }

    @Test
    fun fullStepMoveAdoptsNewLatticePoint() {
        assertEquals(56, nextTimelineRowHeightPx(48, 57, bounds))
    }

    @Test
    fun sampleClampedToBounds() {
        assertEquals(160, nextTimelineRowHeightPx(UNSAMPLED_ROW_HEIGHT_PX, 400, bounds))
        // 4 clampa a 20 e poi scatta al reticolo 8dp più vicino: 24.
        assertEquals(24, nextTimelineRowHeightPx(UNSAMPLED_ROW_HEIGHT_PX, 4, bounds))
    }

    @Test
    fun nullSampleKeepsCurrent() {
        assertEquals(48, nextTimelineRowHeightPx(48, null, bounds))
    }

    @Test
    fun onlyRealNonZeroRowsSampled() {
        val samples = timelineRowHeightSamplesPx(
            listOf(42L to 60, "append" to 48, 43L to 0, 44L to 36),
        )
        assertEquals(listOf(60, 36), samples)
    }
}
