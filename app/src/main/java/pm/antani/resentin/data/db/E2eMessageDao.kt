package pm.antani.resentin.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface E2eMessageDao {
    @Query("SELECT * FROM e2e_messages WHERE networkSlug = :slug AND peer = :peer ORDER BY at, id")
    fun observe(slug: String, peer: String): Flow<List<E2eMessageEntity>>

    /** The newest message of each conversation on the network, newest conversation first. */
    @Query(
        "SELECT * FROM e2e_messages WHERE id IN (SELECT MAX(id) FROM e2e_messages WHERE networkSlug = :slug GROUP BY peer) " +
            "ORDER BY at DESC",
    )
    fun observeConversations(slug: String): Flow<List<E2eMessageEntity>>

    @Insert
    suspend fun insert(message: E2eMessageEntity): Long

    @Query("UPDATE e2e_messages SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("DELETE FROM e2e_messages WHERE networkSlug = :slug AND peer = :peer")
    suspend fun deleteConversation(slug: String, peer: String)
}
