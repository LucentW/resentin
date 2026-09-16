package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

/** Reply to the `away` verb — mirrors cicchetto's `away_confirmed`
 * `{network, state: "away"|"present"}` (wireTypes.ts). */
@Serializable
data class AwayConfirmedDto(
    val network: String,
    val state: String,
)

/** Reply to the `whowas` verb — mirrors cicchetto's `whowas_bundle`; every
 * nullable field is absent on `not_found` (upstream 406). */
@Serializable
data class WhowasBundleDto(
    val network: String,
    val target: String,
    val user: String? = null,
    val host: String? = null,
    val realname: String? = null,
    val server: String? = null,
    val logoffTime: String? = null,
    val notFound: Boolean = false,
)

/** One row of a `who_reply` burst — mirrors cicchetto's `SessionWireWhoUser`. */
@Serializable
data class WhoUserDto(
    val nick: String,
    val user: String = "",
    val host: String = "",
    val server: String = "",
    val modes: String = "",
    val hops: Int? = null,
    val realname: String? = null,
    val channel: String = "",
)

/** Reply to the `who` verb — mirrors cicchetto's `who_reply`
 * `{network, target, users[]}`. */
@Serializable
data class WhoReplyDto(
    val network: String,
    val target: String,
    val users: List<WhoUserDto> = emptyList(),
)

/** Reply to the `lusers` verb — mirrors cicchetto's `lusers_bundle`: the 12
 * RFC 2812 §3.4.2 counters, each nullable because an ircd may omit any of them. */
@Serializable
data class LusersBundleDto(
    val network: String,
    val totalUsers: Int? = null,
    val invisible: Int? = null,
    val servers: Int? = null,
    val operators: Int? = null,
    val unknownConnections: Int? = null,
    val channelsFormed: Int? = null,
    val localClients: Int? = null,
    val localServers: Int? = null,
    val currentLocal: Int? = null,
    val maxLocal: Int? = null,
    val currentGlobal: Int? = null,
    val maxGlobal: Int? = null,
)

/** One row returned by the IRC `LINKS` command. */
@Serializable
data class LinksEntryDto(
    val server: String,
    val linkedTo: String? = null,
    val hopcount: Int? = null,
    val description: String? = null,
)

/** Reply to `/links [mask]` — mirrors cicchetto's `links_bundle`. */
@Serializable
data class LinksBundleDto(
    val network: String,
    val mask: String? = null,
    val entries: List<LinksEntryDto> = emptyList(),
)

/** `phx_reply` response of the `watchlist` verb (`add`/`del`/`list`) — mirrors
 * cicchetto's `{patterns: string[]}`. */
@Serializable
data class WatchlistDto(
    val patterns: List<String> = emptyList(),
)

/** `phx_reply` response of the `resolve_userhost` verb — `{user, host}`. */
@Serializable
data class UserhostDto(
    val user: String,
    val host: String,
)
