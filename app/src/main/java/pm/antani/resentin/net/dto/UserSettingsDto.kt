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

@Serializable
data class VhostOptionDto(
    val address: String,
    val inPool: Boolean = false,
    val granted: Boolean = false,
    /** Reverse-DNS name when one resolves, otherwise the raw address again. */
    val name: String,
)

@Serializable
data class VhostSettingsDto(
    val available: List<VhostOptionDto> = emptyList(),
    /** Addresses currently selected — more than one means "random per connection"
     * server-side, not "first wins". */
    val selection: List<String> = emptyList(),
)

@Serializable
data class VhostSelectionUpdateDto(
    val selection: List<String>,
)

/** #348 on grappa-irc — the auto-away grace period. `null` = no preference (the
 * server's own default applies), `0` = auto-away off, `N` = seconds. This shape is
 * only used to DECODE (GET response and the `auto_away_debounce_changed` push) — a
 * PUT body is hand-built as a [kotlinx.serialization.json.JsonObject] instead, since
 * the app's shared `AppJson` config's `explicitNulls = false` would drop an explicit
 * "clear to site default" `null` from a normal data-class body. See
 * [pm.antani.resentin.net.rest.UserSettingsApi.updateAutoAwayDebounce]. */
@Serializable
data class AutoAwayDebounceDto(
    val autoAwayDebounceSeconds: Int? = null,
)
