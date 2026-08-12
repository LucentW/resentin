package pm.antani.resentin.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(channels: List<ChannelEntity>)

    // Query windows (source = "query") and the synthetic "$server" pseudo-channel
    // (source = "server") aren't returned by GET /networks/:slug/channels, so they must
    // be excluded here — otherwise every REST refresh would wipe them out.
    @Query(
        "DELETE FROM channels WHERE networkSlug = :networkSlug AND name NOT IN (:names) " +
            "AND source NOT IN ('query', 'server')",
    )
    suspend fun deleteMissing(networkSlug: String, names: List<String>)

    @Query("DELETE FROM channels WHERE networkSlug = :networkSlug AND source = 'query' AND name NOT IN (:targetNicks)")
    suspend fun deleteMissingQueries(networkSlug: String, targetNicks: List<String>)

    @Query("UPDATE channels SET topic = :topic WHERE networkSlug = :networkSlug AND name = :name")
    suspend fun updateTopic(networkSlug: String, name: String, topic: String?)

    @Query("UPDATE channels SET modes = :modes WHERE networkSlug = :networkSlug AND name = :name")
    suspend fun updateModes(networkSlug: String, name: String, modes: String?)

    // Mirrors the server's monotonic advance-only clamp on ReadCursor.set/4 — never
    // regress the locally-cached cursor on a stale/out-of-order update.
    @Query(
        "UPDATE channels SET lastReadMessageId = MAX(COALESCE(lastReadMessageId, 0), :messageId) " +
            "WHERE networkSlug = :networkSlug AND name = :name",
    )
    suspend fun advanceLastReadMessageId(networkSlug: String, name: String, messageId: Long)

    @Query("SELECT * FROM channels WHERE networkSlug = :networkSlug AND name = :name")
    fun observeChannel(networkSlug: String, name: String): Flow<ChannelEntity?>

    @Query("SELECT lastReadMessageId FROM channels WHERE networkSlug = :networkSlug AND name = :name")
    suspend fun getLastReadMessageId(networkSlug: String, name: String): Long?

    @Query(
        "UPDATE channels SET unreadMessages = :messages, unreadMentions = :mentions, severity = :severity " +
            "WHERE networkSlug = :networkSlug AND name = :name",
    )
    suspend fun updateUnreadCounts(networkSlug: String, name: String, messages: Int, mentions: Int, severity: String)
}
