package pm.antani.resentin.irc

// Mirrors `Grappa.Dcc.Report.render/2`'s delivered-file wording exactly: either a
// quoted display name or the unquoted "(unnamed)" sentinel, an em dash, then the
// relative download path (`Session.Server.dcc_file_route/2`).
private val DCC_FILE_LINE_REGEX = Regex(
    """(?:"([^"\r\n]+)"|\(unnamed\)) — (/networks/\d+/dcc_files/[a-z0-9]+)""",
)

data class DccFileLink(val pathRange: IntRange, val path: String, val filename: String?)

/** Finds the relative, auth-gated download path a DCC delivery report embeds in a
 * scrollback row (e.g. `📥 "photo.jpg" — /networks/3/dcc_files/abc123`) — a bare path,
 * deliberately not an absolute URL (see `Grappa.Dcc.Report`'s moduledoc: it's meant to
 * be rendered as a same-origin link on cicchetto's web page, which resolves it and
 * carries the session cookie for free). This app has no such thing, so a tap on it must
 * go through the app's own Bearer-authenticated client instead of the platform URI
 * handler a normal http(s) link would get — see [pm.antani.resentin.ui.common.MircText]. */
object DccFileLinkDetector {
    fun find(text: String): List<DccFileLink> = DCC_FILE_LINE_REGEX.findAll(text).map { match ->
        val pathGroup = match.groups[2]!!
        DccFileLink(pathGroup.range, pathGroup.value, match.groups[1]?.value)
    }.toList()
}
