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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)
}
