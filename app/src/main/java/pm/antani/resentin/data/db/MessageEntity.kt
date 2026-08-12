package pm.antani.resentin.data.db

import androidx.room.Entity

@Entity(tableName = "messages", primaryKeys = ["networkSlug", "channelName", "id"])
data class MessageEntity(
    val networkSlug: String,
    val channelName: String,
    val id: Long,
    val serverTime: Long,
    val kind: String,
    val sender: String,
    val body: String?,
    /** JSON-encoded object; shape depends on `kind` (e.g. join/part carry
     * sender_user+sender_host, mode carries modes+args, kick carries target). */
    val metaJson: String = "{}",
)
