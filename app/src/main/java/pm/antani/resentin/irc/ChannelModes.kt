package pm.antani.resentin.irc

/** Renders a channel's mode set as the classic `+modeletters [params]` IRC string (e.g.
 * `"+rnt"`, `"+lnt 50"`) — `null` when there are no modes set, so a caller can skip the
 * `(...)` wrapper entirely rather than rendering an empty pair of parens. */
fun formatChannelModes(modes: List<String>, params: Map<String, String?>): String? {
    if (modes.isEmpty()) return null
    val letters = modes.joinToString("")
    val paramValues = modes.mapNotNull { params[it] }
    return if (paramValues.isEmpty()) "+$letters" else "+$letters ${paramValues.joinToString(" ")}"
}
