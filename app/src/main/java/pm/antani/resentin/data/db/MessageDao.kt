package pm.antani.resentin.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE networkSlug = :networkSlug AND channelName = :channelName ORDER BY id ASC")
    fun observeMessages(networkSlug: String, channelName: String): Flow<List<MessageEntity>>

    @Query("SELECT MAX(id) FROM messages WHERE networkSlug = :networkSlug AND channelName = :channelName")
    suspend fun maxId(networkSlug: String, channelName: String): Long?

    @Query("SELECT MIN(id) FROM messages WHERE networkSlug = :networkSlug AND channelName = :channelName")
    suspend fun minId(networkSlug: String, channelName: String): Long?

    /** Used to reconstruct a local notification after a UnifiedPush wake-up backfills
     * fresh rows — the push payload itself carries no message id to key a reply/mark-read
     * action off of, only the conversation (network+channel). */
    @Query(
        "SELECT * FROM messages WHERE networkSlug = :networkSlug AND channelName = :channelName " +
            "ORDER BY id DESC LIMIT 1",
    )
    suspend fun latestMessage(networkSlug: String, channelName: String): MessageEntity?

    /** Used by the undecryptable-push catch-up sweep to find exactly which rows a
     * backfill just added, so each can be evaluated for notify-worthiness individually
     * (a single wake-up can cover several new messages across a channel). */
    @Query(
        "SELECT * FROM messages WHERE networkSlug = :networkSlug AND channelName = :channelName " +
            "AND id > :afterId ORDER BY id ASC",
    )
    suspend fun messagesAfter(networkSlug: String, channelName: String, afterId: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)
}
