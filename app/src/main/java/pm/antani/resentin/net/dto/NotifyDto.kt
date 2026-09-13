package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class NotifyRequestDto(val nicks: List<String>)

/** One `/notify` (presence watch) entry — GH #247 on grappa-irc. `presence` (the
 * live online/offline map) rides a separate field on the envelope this client
 * doesn't consume; entries are DB-owned and survive reconnects on their own. */
@Serializable
data class NotifyEntryDto(val nick: String)

@Serializable
data class NotifyListDto(val entries: List<NotifyEntryDto> = emptyList())