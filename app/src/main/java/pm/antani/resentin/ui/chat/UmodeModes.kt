package pm.antani.resentin.ui.chat

// User-mode description table + display derivation — cicchetto umodeModes.ts
// parity (#229, #249, #301).
//
// The AVAILABLE set is server-driven (004 RPL_MYINFO via
// supported_umodes_changed); this static table is the FALLBACK for its
// descriptions AND for the available set when the server never advertised.
// Authority: Azzurra's own helpserv umode helpfile (bahamut), not generic
// ircd convention (+d is DEBUG receive, +g is GLOBOPS receive, +S is SSL).
// Unknown letters default to NOT settable (never grant an oper/services
// capability from the client).

data class UmodeInfo(val label: String, val desc: String, val settable: Boolean)

data class AvailableUmode(
    val letter: String,
    val label: String,
    val desc: String,
    val settable: Boolean,
)

private val UMODE_DESCRIPTIONS: Map<String, UmodeInfo> = mapOf(
    "i" to UmodeInfo("invisible", "hidden from WHO / global nick lists", true),
    "w" to UmodeInfo("wallops", "receive network WALLOPS broadcasts", true),
    "s" to UmodeInfo("server notices", "receive server notice broadcasts", true),
    "x" to UmodeInfo("masked host", "cloak your hostname from other users", true),
    "R" to UmodeInfo("reg'd only", "only registered nicks may /msg you", true),
    "b" to UmodeInfo("chatops", "IRCop: receive CHATOPS messages", false),
    "c" to UmodeInfo("client notices", "IRCop: server connect/disconnect notices", false),
    "d" to UmodeInfo("debug notices", "IRCop: receive DEBUG messages", false),
    "e" to UmodeInfo("invalid DCC", "IRCop: receive invalid-DCC notices", false),
    "f" to UmodeInfo("flood notices", "IRCop: receive FLOOD messages", false),
    "g" to UmodeInfo("globops", "IRCop: receive GLOBOPS messages", false),
    "k" to UmodeInfo("kill notices", "IRCop: receive KILL (non-U:Line)", false),
    "K" to UmodeInfo("U:Line kills", "IRCop: receive KILL (U:Lined)", false),
    "m" to UmodeInfo("spam notices", "IRCop: receive SPAM messages", false),
    "n" to UmodeInfo("routing notices", "IRCop: receive ROUTING messages", false),
    "y" to UmodeInfo("command notices", "IRCop: notify on /ADMIN /LINKS /WHOIS …", false),
    "F" to UmodeInfo("flood immune", "IRCop: immune from flood limits", false),
    "I" to UmodeInfo("hide idle", "IRCop: hide idle time in WHOIS", false),
    "j" to UmodeInfo("java user", "chatting from the web via Java", false),
    "S" to UmodeInfo("SSL", "connected to the server via SSL", false),
    "o" to UmodeInfo("operator", "IRC operator (server-granted)", false),
    "O" to UmodeInfo("local op", "local IRC operator (server-granted)", false),
    "r" to UmodeInfo("registered", "identified to NickServ (services-set)", false),
    "a" to UmodeInfo("services admin", "services administrator (services-set)", false),
    "A" to UmodeInfo("server admin", "server administrator (server-set)", false),
    "h" to UmodeInfo("help operator", "services: Help Operator", false),
    "z" to UmodeInfo("services agent", "services: Services Agent", false),
)

fun umodeDescription(letter: String): UmodeInfo =
    UMODE_DESCRIPTIONS[letter] ?: UmodeInfo(
        label = "mode +$letter",
        desc = "user mode (no description available)",
        settable = false,
    )

// REPLACE (not union-with-static) when serverSet is non-empty: 004 is the
// ircd's authoritative list. An active umode always renders even if omitted
// (never hide own active state). Sorted by label for a stable layout.
fun availableUmodes(activeModes: List<String>, serverSet: List<String>): List<AvailableUmode> {
    val base = if (serverSet.isNotEmpty()) serverSet else UMODE_DESCRIPTIONS.keys.toList()
    return (base + activeModes).toSet()
        .map { letter ->
            val info = umodeDescription(letter)
            AvailableUmode(letter, info.label, info.desc, info.settable)
        }
        .sortedBy { it.label }
}
