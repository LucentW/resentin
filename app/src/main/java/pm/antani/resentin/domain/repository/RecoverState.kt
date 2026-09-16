package pm.antani.resentin.domain.repository

// "Recover my identity" progress state — cicchetto recoverProgress.ts parity
// (#581). Server-driven: the first `recover_progress` event OPENS the modal,
// later progress events accumulate/update steps, `recover_result` concludes.
// The client NEVER originates recovery state: no optimistic open, no guessed
// outcome. A result arriving while closed is dropped (no resurrect).

data class RecoverStepEntry(
    val step: String,
    val status: String,
)

data class RecoverState(
    val networkSlug: String,
    // Steps in server-arrival order; each step appears once (later events for
    // the same step UPDATE it in place).
    val steps: List<RecoverStepEntry> = emptyList(),
    // Null while in flight; set by the terminal `recover_result` event.
    val outcome: String? = null,
    val reason: String? = null,
)

// Upsert a step by name: update the existing row in place, else append.
fun upsertRecoverStep(steps: List<RecoverStepEntry>, step: String, status: String): List<RecoverStepEntry> {
    val entry = RecoverStepEntry(step, status)
    val idx = steps.indexOfFirst { it.step == step }
    if (idx == -1) return steps + entry
    return steps.toMutableList().also { it[idx] = entry }
}
