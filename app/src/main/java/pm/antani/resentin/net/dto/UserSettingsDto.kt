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

/** M2 — opt-in to grappa opportunistically querying OTHER users' CTCP USERINFO/AVATAR
 * (JOIN/353-triggered, rate-limited, cache-deduped — see grappa's `EventRouter.
 * maybe_query_peer_profile/2`). Off by default; this is the ONE gate behind both the
 * gender badge and every peer avatar this app can ever show (query rows, notifications).
 * Same GET/PUT shape both ways: `{"show_peer_profiles": bool}`. */
@Serializable
data class ShowPeerProfilesDto(
    val showPeerProfiles: Boolean = false,
)

/**
 * Sezione IRC delle Impostazioni — messaggi PART/QUIT personalizzabili,
 * persistenti server-side (stessa infrastruttura delle altre user settings,
 * quindi cross-device come display-prefs/aliases).
 *
 * `null` = mai personalizzato (il client usa il predefinito
 * `Grappa-IRC - Resentin %version` con versione risolta, senza scriverlo sul
 * server per non congelare la versione né sovrascrivere altri device);
 * `""`/blank = nessun motivo (comportamento attuale senza motivo);
 * qualsiasi altro valore = messaggio personalizzato (`%version` espanso solo
 * all'invio, mai persistito risolto né inviato letteralmente).
 */
@Serializable
data class IrcMessagesDto(
    val partMessage: String? = null,
    val quitMessage: String? = null,
)
