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

@Serializable
data class BanlistEntryDto(
    val mask: String,
    val setter: String? = null,
    val setTs: Long? = null,
)

/** Reply to a `banlist` verb query — one of the channel's type-A (list) modes:
 * `b` bans, `e` exempts, `I` invex, `z`/`q` restrict/quiet (network-dependent). */
@Serializable
data class BanlistBundleDto(
    val network: String,
    val channel: String,
    val mode: String,
    val entries: List<BanlistEntryDto> = emptyList(),
)
