package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class NetworkDto(
    val id: Int,
    val slug: String,
    val kind: String,
    val nick: String,
    val ident: String? = null,
    val realname: String? = null,
    val connectionState: String,
    val connectionStateReason: String? = null,
    val connectionStateChangedAt: String? = null,
    val connection: NetworkConnectionDto? = null,
)

@Serializable
data class NetworkConnectionDto(
    val server: String? = null,
    val port: Int? = null,
    val tls: Boolean? = null,
    val registered: Boolean? = null,
    val connectedAt: String? = null,
)
