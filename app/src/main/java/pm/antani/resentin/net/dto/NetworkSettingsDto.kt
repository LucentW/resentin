package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class IdentityUpdateDto(
    val nick: String? = null,
    val ident: String? = null,
    val realname: String? = null,
)

@Serializable
data class ConnectionStateUpdateDto(
    val connectionState: String,
)

@Serializable
data class PerformDto(
    val performList: String? = null,
    val operPassSet: Boolean = false,
)

@Serializable
data class PerformUpdateDto(
    val performList: String? = null,
)

@Serializable
data class TopicUpdateDto(
    val body: String,
)
