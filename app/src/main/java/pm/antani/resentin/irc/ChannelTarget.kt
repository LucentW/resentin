package pm.antani.resentin.irc

private val CHANNEL_PREFIXES = setOf('#', '&', '+', '!')
private const val SERVER_PSEUDO_CHANNEL = "\$server"

/**
 * True for a query/DM target (a bare nick) — false for a real channel or the
 * "$server" pseudo-channel. RFC 2811's default CHANTYPES ("#&+!"); we don't track a
 * network's actual ISUPPORT CHANTYPES, so this is the closest safe default.
 */
fun isQueryTarget(channelOrNick: String): Boolean =
    channelOrNick != SERVER_PSEUDO_CHANNEL && channelOrNick.firstOrNull() !in CHANNEL_PREFIXES
