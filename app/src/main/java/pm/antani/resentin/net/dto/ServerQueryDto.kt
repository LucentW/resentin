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

/** One server node of a `links_bundle` burst — mirrors cicchetto's
 * `SessionWireLinksEntry`: `server` is the node, `linkedTo` its uplink
 * (the root self-links), `hopcount` its distance, `description` the 364 info. */
@Serializable
data class LinksEntryDto(
    val server: String,
    val linkedTo: String? = null,
    val hopcount: Int? = null,
    val description: String? = null,
)

/** Reply to the `links` verb — mirrors cicchetto's `links_bundle`
 * `{network, mask, entries[]}`. An empty `entries` is still a snapshot:
 * a restricted topology (null mask) or a mask that matched nothing. */
@Serializable
data class LinksBundleDto(
    val network: String,
    val mask: String? = null,
    val entries: List<LinksEntryDto> = emptyList(),
)

/** Operator's own umodes for one network — mirrors cicchetto's `umode_changed`
 * `{network_id, modes[]}` (221 RPL_UMODEIS + self-MODE echoes, sign stripped). */
@Serializable
data class UmodeChangedDto(
    val networkId: Int,
    val modes: List<String> = emptyList(),
)

/** Server-advertised supported umodes (004 RPL_MYINFO) — mirrors cicchetto's
 * `supported_umodes_changed`. Empty = unseeded -> static fallback table. */
@Serializable
data class SupportedUmodesChangedDto(
    val networkId: Int,
    val modes: List<String> = emptyList(),
)

/** Reply to an `info`/`version`/`motd`/`admin` verb — mirrors cicchetto's
 * `server_reply` `{network, source, lines[]}`. Monospace server free-text,
 * rendered verbatim via MircText (may carry mIRC control bytes). */
@Serializable
data class ServerReplyDto(
    val network: String,
    val source: String,
    val lines: List<String> = emptyList(),
)

/** One `recover_progress` step transition — mirrors cicchetto's wire event.
 * `step`/`status` stay plain strings on the wire arm (additive-only); the
 * modal localizes the known tokens and renders unknown steps raw, never dropped. */
@Serializable
data class RecoverProgressDto(
    val network: String,
    val step: String,
    val status: String,
    val reason: String? = null,
)

/** Terminal `recover_result` outcome for one network. */
@Serializable
data class RecoverResultDto(
    val network: String,
    val outcome: String,
    val reason: String? = null,
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
