package pm.antani.resentin.ui.common

import androidx.compose.ui.graphics.Color

// A curated palette rather than an HSL-from-hash sweep — keeps every color legible
// against both light and dark surfaces without a separate per-theme table, and avoids
// muddy/low-contrast hues a pure hash could land on.
private val NICK_PALETTE = listOf(
    Color(0xFFE57373),
    Color(0xFFF06292),
    Color(0xFFBA68C8),
    Color(0xFF9575CD),
    Color(0xFF7986CB),
    Color(0xFF64B5F6),
    Color(0xFF4FC3F7),
    Color(0xFF4DB6AC),
    Color(0xFF81C784),
    Color(0xFFAED581),
    Color(0xFFFFB74D),
    Color(0xFFA1887F),
)

/** Deterministic, stable per-nick color from [NICK_PALETTE] — the same nick always
 * lands on the same color (a pure hash, not random), so it stays a usable visual
 * anchor for "who said that" across a session and across app restarts. Case-folded
 * so `Foo`/`foo` (the same IRC identity under every common casemapping) match. */
fun colorForNick(nick: String): Color {
    val hash = nick.lowercase().fold(0) { acc, c -> acc * 31 + c.code }
    return NICK_PALETTE[Math.floorMod(hash, NICK_PALETTE.size)]
}
