package pm.antani.resentin.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "isupport")
data class IsupportEntity(
    @PrimaryKey val networkSlug: String,
    /** JSON-encoded `Map<String, String>` of mode letter -> sigil, e.g. `{"o":"@","v":"+"}`. */
    val prefixJson: String,
)
