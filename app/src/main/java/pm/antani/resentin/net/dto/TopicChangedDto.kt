package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class TopicInfoDto(
    val text: String? = null,
    val setBy: String? = null,
    val setAt: String? = null,
)

@Serializable
data class TopicChangedDto(
    val network: String,
    val channel: String,
    val topic: TopicInfoDto,
)
