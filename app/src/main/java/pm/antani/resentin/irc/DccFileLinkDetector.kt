package pm.antani.resentin.irc

// Mirrors `Grappa.Dcc.Report.render/2`'s delivered-file wording exactly: either a
// quoted display name or the unquoted "(unnamed)" sentinel, an em dash, then the
// ABSOLUTE download URL (`Grappa.Dcc.public_url/2`: public origin + `/dcc_files/`
// + 26-char base32 slug, plus the peer filename's decorative extension when it has
// one — ASCII alphanumerics, max 8 chars).
//
// New-server shape only: the pre-2127 relative `/networks/<id>/dcc_files/<slug>`
// route is gone server-side, so rows in that shape no longer match on purpose.
private val DCC_FILE_LINE_REGEX = Regex(
    """(?:"([^"\r\n]+)"|\(unnamed\)) — (https?://[^\s<>"']+/dcc_files/[a-z2-7]{26}(?:\.[A-Za-z0-9]{1,8})?)""",
)

data class DccFileLink(val pathRange: IntRange, val path: String, val filename: String?)

/** Finds the absolute download URL a DCC delivery report embeds in a scrollback
 * row (e.g. `📥 "photo.jpg" — https://irc.example.com/dcc_files/abc234xyz.jpg`).
 * Absolute since issue 2127 made the file door public (the slug IS the credential,
 * no bearer needed) — which is also why the URL would otherwise match the generic
 * [UrlDetector]: callers must prefer this link over the plain-URL one so the tap
 * opens the app's own save flow instead of the platform browser (see
 * [pm.antani.resentin.ui.common.MircText]). */
object DccFileLinkDetector {
    fun find(text: String): List<DccFileLink> = DCC_FILE_LINE_REGEX.findAll(text).map { match ->
        val pathGroup = match.groups[2]!!
        DccFileLink(pathGroup.range, pathGroup.value, match.groups[1]?.value)
    }.toList()
}
