package pm.antani.resentin.net.ws

import kotlin.random.Random

private const val MIN_BACKOFF_MS = 1_000L
private const val MAX_BACKOFF_MS = 60_000L
private const val JITTER_FRACTION = 0.25

/**
 * Exponential backoff with jitter: 1s -> 2s -> 4s -> ... capped at 60s, reset on
 * a successful reconnect. Mirrors shottino's reconnect policy (same wire contract,
 * proven approach) so a server restart doesn't cause a thundering herd of clients.
 */
class ReconnectPolicy(private val random: Random = Random.Default) {

    private var attempt = 0

    fun nextDelayMs(): Long {
        val exponential = (MIN_BACKOFF_MS shl attempt.coerceAtMost(6)).coerceAtMost(MAX_BACKOFF_MS)
        attempt++
        val jitterRange = (exponential * JITTER_FRACTION).toLong()
        val jitter = if (jitterRange > 0) random.nextLong(-jitterRange, jitterRange + 1) else 0
        return (exponential + jitter).coerceIn(MIN_BACKOFF_MS, MAX_BACKOFF_MS)
    }

    fun reset() {
        attempt = 0
    }
}
