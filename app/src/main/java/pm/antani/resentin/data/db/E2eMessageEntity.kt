package pm.antani.resentin.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Our own decrypted copy of an encrypted message. grappa never stores one (by design: an E2E line
 * is never in scrollback), so this table IS the history. [peer] is the ASCII-folded nick (the
 * conversation key), [peerNick] the spelling to show.
 */
@Entity(tableName = "e2e_messages", indices = [Index("networkSlug", "peer")])
data class E2eMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val networkSlug: String,
    val peer: String,
    val peerNick: String,
    val outgoing: Boolean,
    val body: String,
    val at: Long,
    /** pending (waiting for keys, or on its way) | sent (grappa took every line) | failed. Incoming rows: sent. */
    val status: String = "sent",
)
