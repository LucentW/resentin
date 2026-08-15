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

    /** Used for JOIN: IGNORE (not REPLACE) so an out-of-order JOIN arriving after the
     * member is already known (e.g. right after a `members_seeded` snapshot) doesn't
     * reset their modes back to empty. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(member: MemberEntity)

    @Query("SELECT * FROM members WHERE networkSlug = :networkSlug AND channelName = :channelName AND nick = :nick COLLATE NOCASE LIMIT 1")
    suspend fun find(networkSlug: String, channelName: String, nick: String): MemberEntity?

    @Query("UPDATE members SET modesJson = :modesJson WHERE networkSlug = :networkSlug AND channelName = :channelName AND nick = :nick COLLATE NOCASE")
    suspend fun updateModes(networkSlug: String, channelName: String, nick: String, modesJson: String)

    @Query("UPDATE members SET nick = :newNick WHERE networkSlug = :networkSlug AND channelName = :channelName AND nick = :oldNick COLLATE NOCASE")
    suspend fun rename(networkSlug: String, channelName: String, oldNick: String, newNick: String)

    @Query("DELETE FROM members WHERE networkSlug = :networkSlug AND channelName = :channelName")
    suspend fun clear(networkSlug: String, channelName: String)

    @Query("DELETE FROM members WHERE networkSlug = :networkSlug AND channelName = :channelName AND nick = :nick COLLATE NOCASE")
    suspend fun delete(networkSlug: String, channelName: String, nick: String)
}
