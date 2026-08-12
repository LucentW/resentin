package pm.antani.resentin.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "networks")
data class NetworkEntity(
    @PrimaryKey val slug: String,
    val id: Int,
    val nick: String,
    val ident: String?,
    val realname: String?,
    val connectionState: String,
    val connectionStateReason: String?,
    val connectionStateChangedAt: String?,
    val server: String?,
    val port: Int?,
    val tls: Boolean?,
)
