package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class QueryWindowDto(
    val networkId: Int,
    val openedAt: String,
    val targetNick: String,
)

/** Keyed by network id as a string, per the wire shape observed live. */
@Serializable
data class QueryWindowsListDto(
    val windows: Map<String, List<QueryWindowDto>>,
)
