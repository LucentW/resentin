package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChannelDto(
    val name: String,
    val source: String,
    val joined: Boolean,
)
