package pm.antani.resentin.domain.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class RecoverStateTest {
    @Test
    fun upsertAppendsThenUpdatesInPlace() {
        val steps = upsertRecoverStep(emptyList(), "identify", "running")
        assertEquals(listOf(RecoverStepEntry("identify", "running")), steps)
        val updated = upsertRecoverStep(steps, "identify", "ok")
        assertEquals(listOf(RecoverStepEntry("identify", "ok")), updated)
        val appended = upsertRecoverStep(updated, "nick", "running")
        assertEquals(
            listOf(RecoverStepEntry("identify", "ok"), RecoverStepEntry("nick", "running")),
            appended,
        )
    }
}
