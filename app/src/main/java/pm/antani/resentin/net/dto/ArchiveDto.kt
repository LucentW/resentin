package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable
import pm.antani.resentin.irc.canonicalTarget

@Serializable
data class ArchiveEntryDto(
    val target: String,
    /** `"channel"` or `"query"`. */
    val kind: String,
    /** Epoch MILLISECONDS of the most recent scrollback row for this target
     * (`max(server_time)`, and `server_time` is milliseconds — see grappa's
     * `Grappa.Scrollback.Message` schema doc). */
    val lastActivity: Long,
    val rowCount: Int,
    // #2099 — per-window unread, present when the server includes it in the
    // archive payload. Defaults keep old servers decoding; the repository also
    // merges `/me` unreadCounts as fallback (the same authoritative source
    // Home badges use), so the rollup works either way.
    val unreadMessages: Int = 0,
    val unreadMentions: Int = 0,
    val severity: String = "none",
)

@Serializable
data class ArchiveEnvelopeDto(
    val archive: List<ArchiveEntryDto> = emptyList(),
)

/** An archive row joined with its unread state — see [mergeArchiveUnread]. */
data class ArchiveEntryWithUnread(
    val entry: ArchiveEntryDto,
    val unreadMessages: Int = 0,
    val unreadMentions: Int = 0,
    val severity: String = "none",
)

/** Joins archive rows with the `/me` unread snapshot for one network slug.
 * `/me` wins when present (same source Home badges render from); otherwise the
 * row's own payload fields stand. Matching is [canonicalTarget]-folded, like
 * every other target comparison — the server folds `A-Z` in persisted keys. */
fun mergeArchiveUnread(
    entries: List<ArchiveEntryDto>,
    unreadCounts: Map<String, UnreadCountDto>,
): List<ArchiveEntryWithUnread> {
    if (unreadCounts.isEmpty()) {
        return entries.map {
            ArchiveEntryWithUnread(it, it.unreadMessages, it.unreadMentions, it.severity)
        }
    }
    val folded = unreadCounts.entries.associate { (key, value) -> canonicalTarget(key) to value }
    return entries.map { entry ->
        val fromMe = folded[canonicalTarget(entry.target)]
        if (fromMe != null) {
            ArchiveEntryWithUnread(entry, fromMe.messages, fromMe.mentions, fromMe.severity)
        } else {
            ArchiveEntryWithUnread(entry, entry.unreadMessages, entry.unreadMentions, entry.severity)
        }
    }
}

/** #2099 rollup: total unread across archived windows, shown on the archive
 * launcher. Mirrors Home's per-row semantics — messages and mentions summed
 * separately so the launcher can keep the `@`-mention styling. */
fun List<ArchiveEntryWithUnread>.unreadMessagesRollup(): Int = sumOf { it.unreadMessages }

fun List<ArchiveEntryWithUnread>.unreadMentionsRollup(): Int = sumOf { it.unreadMentions }
