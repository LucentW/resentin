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
    // KVIrc-style CTCP USERINFO profile + M3a avatar — per (subject, network).
    val profileAge: String?,
    val profileGender: String?,
    val profileLocation: String?,
    val profileLanguages: String?,
    val profileCustom: String?,
    val avatarUrl: String?,
    // Mirrors NetworkDto.servicesFlavor — the wizard's per-network REGISTER /
    // verify template key. Persisted so the Home launcher gates without a refetch.
    val servicesFlavor: String? = null,
)
