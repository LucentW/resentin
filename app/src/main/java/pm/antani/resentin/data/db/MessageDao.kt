package pm.antani.resentin.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE networkSlug = :networkSlug AND channelName = :channelName ORDER BY id ASC")
    fun observeMessages(networkSlug: String, channelName: String): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE networkSlug = :networkSlug AND channelName = :channelName")
    suspend fun countMessages(networkSlug: String, channelName: String): Int
    @Query("SELECT COUNT(*) FROM messages WHERE networkSlug = :networkSlug AND channelName = :channelName")
    fun observeMessageCount(networkSlug: String, channelName: String): Flow<Int>

    @Query("SELECT * FROM messages WHERE networkSlug = :networkSlug AND channelName = :channelName ORDER BY id ASC LIMIT :limit OFFSET :offset")
    suspend fun loadMessagesPage(networkSlug: String, channelName: String, limit: Int, offset: Int): List<MessageEntity>

    /** Keyset paging for [MessagePagingSource]: newest-first windows that stay stable
     * when new rows arrive (OFFSET windows shift under inserts and skip/duplicate).
     * Keys are message ids; bounds are exclusive except [loadPageAtOrBefore], which
     * anchors a refresh on the visible row itself. */
    @Query("SELECT * FROM messages WHERE networkSlug = :networkSlug AND channelName = :channelName ORDER BY id DESC LIMIT :limit")
    suspend fun loadNewestPage(networkSlug: String, channelName: String, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE networkSlug = :networkSlug AND channelName = :channelName AND id <= :maxId ORDER BY id DESC LIMIT :limit")
    suspend fun loadPageAtOrBefore(networkSlug: String, channelName: String, maxId: Long, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE networkSlug = :networkSlug AND channelName = :channelName AND id > :minId ORDER BY id ASC LIMIT :limit")
    suspend fun loadNewerThan(networkSlug: String, channelName: String, minId: Long, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE networkSlug = :networkSlug AND channelName = :channelName AND id < :maxId ORDER BY id DESC LIMIT :limit")
    suspend fun loadOlderThan(networkSlug: String, channelName: String, maxId: Long, limit: Int): List<MessageEntity>


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

    /** Latest chat message per channel — the Home row preview: for each (networkSlug,
     * channelName) the most recent real conversation row (privmsg/action/notice; joins,
     * parts, mode changes and the like aren't "messages sent"). Only channels that have
     * at least one such row appear in the result. */
    @Query(
        "SELECT * FROM messages msg " +
            "WHERE msg.kind IN ('privmsg', 'action', 'notice') " +
            "AND msg.id = (SELECT MAX(m2.id) FROM messages m2 " +
            "WHERE m2.networkSlug = msg.networkSlug " +
            "AND m2.channelName = msg.channelName " +
            "AND m2.kind IN ('privmsg', 'action', 'notice')) " +
            "ORDER BY msg.serverTime ASC",
    )
    fun observeLatestPerChannel(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<MessageEntity>)

    /** Startup retention sweep (see ChatRepository.pruneOldMessages) — server backfill
     * can always refetch anything actually needed again, so an old, already-read row is
     * safe to drop locally rather than let the cache grow forever. */
    @Query("DELETE FROM messages WHERE networkSlug = :networkSlug AND channelName COLLATE NOCASE = :channelName")
    suspend fun deleteChannel(networkSlug: String, channelName: String)

    /** Everything in one channel strictly older than [minId] — used when a catch-up
     * lands on the newest server page instead of connecting to the local cache: rows
     * below the resulting hole are dropped so the cache stays ONE contiguous range,
     * and scroll-back (which resumes from the local minimum) asks the server for the
     * backlog instead of being unable to cross the hole. */
    @Query("DELETE FROM messages WHERE networkSlug = :networkSlug AND channelName COLLATE NOCASE = :channelName AND id < :minId")
    suspend fun deleteChannelBefore(networkSlug: String, channelName: String, minId: Long)

    @Query("DELETE FROM messages WHERE serverTime < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    /** The manual "svuota database messaggi" settings action — every channel's local
     * scrollback cache, gone; the server is untouched and a channel simply re-backfills
     * from scratch next time it's opened. */
    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
