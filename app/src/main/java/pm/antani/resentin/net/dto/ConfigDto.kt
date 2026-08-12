package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class ConfigDto(
    val server: String,
    val version: String,
    val protocolVersion: Int,
    val minProtocolVersion: Int,
)
