package pm.antani.resentin.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkDao {

    @Transaction
    @Query("SELECT * FROM networks ORDER BY slug")
    fun observeNetworksWithChannels(): Flow<List<NetworkWithChannels>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(networks: List<NetworkEntity>)

    @Query("DELETE FROM networks WHERE slug NOT IN (:slugs)")
    suspend fun deleteMissing(slugs: List<String>)

    @Query("SELECT slug FROM networks WHERE id = :networkId")
    suspend fun slugForId(networkId: Int): String?

    @Query("SELECT id FROM networks WHERE slug = :slug")
    suspend fun idForSlug(slug: String): Int?

    @Query("SELECT nick FROM networks WHERE slug = :slug")
    suspend fun nickForSlug(slug: String): String?

    @Query("SELECT * FROM networks WHERE slug = :slug")
    fun observeNetwork(slug: String): Flow<NetworkEntity?>
}
