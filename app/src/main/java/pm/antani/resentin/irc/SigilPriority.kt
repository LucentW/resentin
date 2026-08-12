package pm.antani.resentin.irc

/** Standard IRC channel-privilege sigil hierarchy, highest first: ~ owner, & admin
 * (protect), @ op, % half-op, + voice. Shared by member-list ordering and the
 * role-prefix shown next to a nick in chat, so both agree on the same priority. */
val SIGIL_PRIORITY: List<Char> = listOf('~', '&', '@', '%', '+')

/** The highest-priority sigil present in [sigils] (as decoded from a member's raw mode
 * list), or null if they hold none of the standard privilege sigils. */
fun highestSigil(sigils: String): Char? = SIGIL_PRIORITY.firstOrNull { it in sigils }
