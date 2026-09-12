package pm.antani.resentin.ui.login

import android.net.Uri

/** `grappa://<hostname>/<token>` — the magic-link login format grappa-irc's own
 * QR/link generator produces (for TOTP/passkey-gated accounts, which can't use the
 * plain username+password flow this client has). Parsed the same way whether it
 * arrives as a real Android deep link (MainActivity's intent-filter) or as a raw URL
 * pasted into the host field (LoginViewModel.onHostFieldBlur). */
fun parseGrappaLoginLink(text: String): Pair<String, String>? {
    val uri = runCatching { Uri.parse(text.trim()) }.getOrNull() ?: return null
    if (uri.scheme != "grappa") return null
    val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
    val token = uri.path?.trimStart('/')?.takeIf { it.isNotBlank() } ?: return null
    return host to token
}

/** `https://<host>/share#<token>` — cicchetto's session-sharing QR/link
 * (`ShareSessionModal.tsx`'s "open on another device"). The share token IS the
 * credential and rides the URL FRAGMENT rather than a path/query segment (#1404
 * on grappa-irc — a fragment is never sent to the server or a `Referer`), so
 * [Uri.getFragment] (already percent-decoded by Android, same as cic's own
 * `decodeURIComponent`) is what carries it, not the path. `host` includes the
 * port when the QR's origin had a non-default one, matching how the login
 * screen's own host field is later stripped of scheme in [LoginViewModel.signIn]. */
fun parseShareLink(text: String): Pair<String, String>? {
    val uri = runCatching { Uri.parse(text.trim()) }.getOrNull() ?: return null
    if (uri.scheme != "http" && uri.scheme != "https") return null
    val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
    val hostWithPort = if (uri.port != -1) "$host:${uri.port}" else host
    val shareToken = uri.fragment?.takeIf { it.isNotBlank() } ?: return null
    return hostWithPort to shareToken
}
