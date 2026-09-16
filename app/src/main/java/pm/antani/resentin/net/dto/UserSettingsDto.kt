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
    // #2029 — strip mIRC formatting on render (opt-in, off by default so colors
    // keep working until the reader asks them to stop).
    val stripFormatting: Boolean = false,
    // #1766 — mobile window bar (opt-OUT, default shown). No native equivalent yet,
    // but it must round-trip: omitting it would reset a cicchetto choice on every
    // full-map PUT from this client.
    val showBottomBar: Boolean = true,
    // #2037 — sidebar events pill (opt-in). Same round-trip reasoning as above.
    val showEventBadge: Boolean = false,
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

/** Issue 2150 on grappa-irc — the QUIT/PART default message, sent when the subject
 * leaves without typing one (`/quit`, `/part`, the sidebar ×). `null` = nothing
 * stored (the server falls back to its own literal — never printed from here).
 * Decode-only, like [AutoAwayDebounceDto]: the PUT body is hand-built, and an
 * emptied input posts `""`, which the server normalises to "no default". */
@Serializable
data class QuitPartReasonDto(
    val quitPartReason: String? = null,
)

/** Issue 2150 on grappa-irc — what the bouncer sends when IT marks the subject away
 * after the auto-away debounce fires. `null` = the server keeps its own constant,
 * which this client deliberately does not know. Same decode-only + `""`-clears
 * discipline as [QuitPartReasonDto]. */
@Serializable
data class AutoAwayReasonDto(
    val autoAwayReason: String? = null,
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
