package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class MemberDto(
    val nick: String,
    val modes: List<String> = emptyList(),
)

@Serializable
data class MembersSeededDto(
    val network: String,
    val channel: String,
    val members: List<MemberDto> = emptyList(),
)

@Serializable
data class WhoisBundleDto(
    val target: String,
    val user: String? = null,
    val host: String? = null,
    val realname: String? = null,
    val server: String? = null,
    val serverInfo: String? = null,
    val isOperator: Boolean = false,
    val operText: String? = null,
    val isAdmin: Boolean = false,
    val isServicesAdmin: Boolean = false,
    val isChanop: Boolean = false,
    val isHelper: Boolean = false,
    val isRegistered: Boolean = false,
    val idleSeconds: Long? = null,
    val signon: Long? = null,
    val channels: List<String>? = null,
    val account: String? = null,
    val awayMessage: String? = null,
    val secure: Boolean = false,
    val usingSsl: Boolean = false,
    val umodes: List<String>? = null,
)
