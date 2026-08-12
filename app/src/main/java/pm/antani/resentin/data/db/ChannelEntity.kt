package pm.antani.resentin.data.db

import androidx.room.Entity

@Entity(tableName = "channels", primaryKeys = ["networkSlug", "name"])
data class ChannelEntity(
    val networkSlug: String,
    val name: String,
    val source: String,
    val joined: Boolean,
    val topic: String? = null,
    /** Pre-formatted `+modeletters [params]` (e.g. `"+rnt"`, `"+lnt 50"`), ready to
     * render as `(+rnt)` — see [pm.antani.resentin.irc.formatChannelModes]. */
    val modes: String? = null,
    /** The server's monotonic read-cursor: the last message id this client has told
     * the server it read. Also drives where a freshly-opened chat scrolls to. */
    val lastReadMessageId: Long? = null,
    // Server-authoritative unread snapshot (the `window_counts` door): seeded from a
    // channel join's response, kept live via the `window_counts` broadcast on the same
    // topic. `severity` is the server's closed set: "mention" | "message" | "event" | "none".
    val unreadMessages: Int = 0,
    val unreadMentions: Int = 0,
    val severity: String = "none",
)
