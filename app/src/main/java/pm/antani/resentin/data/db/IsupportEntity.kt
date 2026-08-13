package pm.antani.resentin.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "isupport")
data class IsupportEntity(
    @PrimaryKey val networkSlug: String,
    /** JSON-encoded `Map<String, String>` of mode letter -> sigil, e.g. `{"o":"@","v":"+"}`. */
    val prefixJson: String,
    /** JSON-encoded `List<String>` of type-A (list) mode letters this network's ircd
     * will answer a `banlist` query for, e.g. `["b","e","I"]` — see
     * `list_modes_queryable` on the server's `isupport_changed`. */
    val listModesQueryableJson: String = "[]",
)
