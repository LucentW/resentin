package pm.antani.resentin.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class NetworkWithChannels(
    @Embedded val network: NetworkEntity,
    @Relation(parentColumn = "slug", entityColumn = "networkSlug")
    val channels: List<ChannelEntity>,
)
