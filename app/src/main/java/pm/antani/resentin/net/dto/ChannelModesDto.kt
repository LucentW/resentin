package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChannelModesEntryDto(
    val modes: List<String> = emptyList(),
    val params: Map<String, String?> = emptyMap(),
)

@Serializable
data class ChannelModesChangedDto(
    val network: String,
    val channel: String,
    val modes: ChannelModesEntryDto,
)
