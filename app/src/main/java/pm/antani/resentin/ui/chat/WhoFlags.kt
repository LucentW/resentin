package pm.antani.resentin.ui.chat

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import pm.antani.resentin.data.db.MemberEntity
import pm.antani.resentin.net.AppJson
import pm.antani.resentin.net.dto.WhoUserDto

// `/who` row decode — cicchetto WhoModal parity (#169/#176/#272, issue 1999).
//
// The 352 flags token on bahamut is positional:
//   [ H | G ] · [ * | % ] · [ S ] · [ @ | % | + ]
//     away      oper|+i     ssl     chanop|halfop|voice
// Position-2 `%` is umode +i (operator WHO view only); position-4 `%` is the
// halfop membership prefix. A lone `%` is undecidable from the token alone,
// so membership is roster-authoritative (NAMES snapshot) and invisibility is
// `%`-count-reconciled against it — both directions (see resolveWhoRow).
// Membership sigils are per-network (005 PREFIX), never a closed union.

private val STRUCTURAL_WHO_FLAGS = setOf('H', 'G', '*', 'S')

data class WhoFlags(
    val awayGone: Boolean,
    val oper: Boolean,
    val secure: Boolean,
    // Trailing-glyph fallback, used only when no NAMES snapshot resolves the row.
    val membership: String?,
    val unknown: List<String>,
)

data class ResolvedWhoRow(
    val awayGone: Boolean,
    val oper: Boolean,
    val invisible: Boolean,
    val secure: Boolean,
    val membership: String?,
    val unknown: List<String>,
)

data class WhoFlagChip(val label: String)

// Roster answer for one nick. Null-vs-missing matters: a roster-plain member
// must NOT fall through to the flags field (stray oper-view `%`).
sealed interface RosterMembership {
    data class Sigil(val sigil: String) : RosterMembership
    data object Plain : RosterMembership
    data object NoSnapshot : RosterMembership
}

fun parseWhoFlags(raw: String, rank: List<String>): WhoFlags {
    val chars = raw.toList()
    val last = chars.lastOrNull()?.toString()
    val rankSet = rank.toSet()
    return WhoFlags(
        awayGone = chars.firstOrNull() == 'G',
        oper = '*' in chars,
        secure = 'S' in chars,
        membership = if (last != null && last in rankSet) last else null,
        unknown = chars.map { it.toString() }
            .filter { it.length == 1 && it[0] !in STRUCTURAL_WHO_FLAGS && it !in rankSet },
    )
}

fun resolveWhoRow(modes: String, roster: RosterMembership, rank: List<String>): ResolvedWhoRow {
    val flags = parseWhoFlags(modes, rank)
    val membership = when (roster) {
        is RosterMembership.Sigil -> roster.sigil
        is RosterMembership.Plain -> null
        is RosterMembership.NoSnapshot -> flags.membership
    }
    val percentCount = modes.count { it == '%' }
    return ResolvedWhoRow(
        awayGone = flags.awayGone,
        oper = flags.oper,
        invisible = percentCount > (if (membership == "%") 1 else 0),
        secure = flags.secure,
        membership = membership,
        unknown = flags.unknown,
    )
}

private val CLASSIC_MEMBERSHIP_LABEL = mapOf("@" to "chanop", "%" to "halfop", "+" to "voice")
private val MEMBERSHIP_MODE_NAMES = mapOf(
    "q" to "founder",
    "a" to "admin",
    "o" to "ops",
    "h" to "halfop",
    "v" to "voice",
)

// Names the membership level for a sigil on THIS network via reverse PREFIX
// lookup (letter -> sigil map). Unknown letter -> "mode +<letter>";
// unadvertised sigil -> raw sigil (never invent a level).
fun membershipLevelName(sigil: String, prefix: Map<String, String>): String {
    CLASSIC_MEMBERSHIP_LABEL[sigil]?.let { return it }
    val letter = prefix.entries.firstOrNull { it.value == sigil }?.key ?: return sigil
    return MEMBERSHIP_MODE_NAMES[letter] ?: "mode +$letter"
}

fun whoChips(row: ResolvedWhoRow, prefix: Map<String, String>): List<WhoFlagChip> {
    val chips = mutableListOf(WhoFlagChip(if (row.awayGone) "gone" else "here"))
    if (row.oper) chips += WhoFlagChip("ircop")
    if (row.invisible) chips += WhoFlagChip("invisible")
    if (row.secure) chips += WhoFlagChip("secure")
    if (row.membership != null) chips += WhoFlagChip(membershipLevelName(row.membership, prefix))
    row.unknown.forEach { chips += WhoFlagChip(it) }
    return chips
}

// Highest-ranked sigil in [modes] per [rank], or null for plain.
// Rank is the network's own run, never the arrival order.
fun memberSigilFor(modes: List<String>, rank: List<String>): String? =
    rank.firstOrNull { it in modes }

// Network sigil run, highest first: standard hierarchy intersected with what
// the network's own PREFIX advertises, unknown advertised sigils appended.
// Falls back to the bahamut run when unseeded (same as cicchetto DEFAULT).
fun sigilRankFor(prefix: Map<String, String>): List<String> {
    val standard = listOf("~", "&", "@", "%", "+")
    val advertised = prefix.values.toSet()
    if (advertised.isEmpty()) return listOf("@", "%", "+")
    val ranked = standard.filter { it in advertised }
    val extra = advertised.filter { it !in standard.toSet() }.sorted()
    return (ranked + extra).ifEmpty { listOf("@", "%", "+") }
}

// Roster answer for one WHO row using THIS chat's NAMES snapshot. We only hold
// the current channel's members live, so rows for another channel (or a mask
// WHO with no channel context) fall back to the trailing-glyph parse
// (RosterMembership.NoSnapshot) — same fallback cicchetto uses with no snapshot.
fun whoRosterFor(
    user: WhoUserDto,
    target: String,
    currentChannel: String,
    members: List<MemberEntity>,
    rank: List<String>,
): RosterMembership {
    if (members.isEmpty()) return RosterMembership.NoSnapshot
    val rowChannel = user.channel
    val useRoster = rowChannel.equals(currentChannel, ignoreCase = true) ||
        ((rowChannel.isBlank() || rowChannel == "*") && target.equals(currentChannel, ignoreCase = true))
    if (!useRoster) return RosterMembership.NoSnapshot
    val member = members.firstOrNull { it.nick.equals(user.nick, ignoreCase = true) }
        ?: return RosterMembership.NoSnapshot
    val modes = runCatching {
        AppJson.decodeFromString(ListSerializer(String.serializer()), member.modesJson)
    }.getOrDefault(emptyList())
    val sigil = memberSigilFor(modes, rank)
    return if (sigil == null) RosterMembership.Plain else RosterMembership.Sigil(sigil)
}
