package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class IsupportChangedDto(
    val networkId: Int,
    val prefix: Map<String, String> = emptyMap(),
    val listModesQueryable: List<String> = emptyList(),
)
