package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class DisplayPrefsEnvelopeDto(
    val displayPrefs: DisplayPrefsDto,
)

@Serializable
data class DisplayPrefsDto(
    val coloredNicklist: Boolean = false,
    val timeFormat: String = "hms",
    // The server rejects a PUT missing this field ("presence_filter must be a map"),
    // so it must round-trip even though this client doesn't yet expose editing it.
    val presenceFilter: Map<String, String> = emptyMap(),
)

@Serializable
data class AliasesEnvelopeDto(
    val aliases: Map<String, String> = emptyMap(),
)
