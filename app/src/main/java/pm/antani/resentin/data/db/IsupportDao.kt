package pm.antani.resentin.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IsupportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(isupport: IsupportEntity)

    @Query("SELECT * FROM isupport WHERE networkSlug = :networkSlug")
    fun observeIsupport(networkSlug: String): Flow<IsupportEntity?>
}
