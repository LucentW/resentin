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
