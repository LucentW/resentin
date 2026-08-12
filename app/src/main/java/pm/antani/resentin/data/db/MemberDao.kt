package pm.antani.resentin.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE networkSlug = :networkSlug AND channelName = :channelName ORDER BY nick")
    fun observeMembers(networkSlug: String, channelName: String): Flow<List<MemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(members: List<MemberEntity>)

    @Query("DELETE FROM members WHERE networkSlug = :networkSlug AND channelName = :channelName")
    suspend fun clear(networkSlug: String, channelName: String)
}
