package pm.antani.resentin.data.db

import androidx.room.Entity

@Entity(tableName = "members", primaryKeys = ["networkSlug", "channelName", "nick"])
data class MemberEntity(
    val networkSlug: String,
    val channelName: String,
    val nick: String,
    /** JSON-encoded `List<String>` of mode sigils, e.g. `["@"]` — the wire already
     * sends sigils, not raw mode letters, so no ISUPPORT-prefix translation needed here. */
    val modesJson: String,
)
