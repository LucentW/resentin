package pm.antani.resentin.net.rest

import kotlinx.serialization.json.JsonObject
import pm.antani.resentin.net.dto.AliasesEnvelopeDto
import pm.antani.resentin.net.dto.AutoAwayDebounceDto
import pm.antani.resentin.net.dto.IrcMessagesDto
import pm.antani.resentin.net.dto.NotificationPrefsEnvelopeDto
import pm.antani.resentin.net.dto.DisplayPrefsEnvelopeDto
import pm.antani.resentin.net.dto.ShowPeerProfilesDto
import pm.antani.resentin.net.dto.VhostSelectionUpdateDto
import pm.antani.resentin.net.dto.VhostSettingsDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

interface UserSettingsApi {
    @GET("me/settings/display-prefs")
    suspend fun getDisplayPrefs(): DisplayPrefsEnvelopeDto

    @PUT("me/settings/display-prefs")
    suspend fun updateDisplayPrefs(@Body body: DisplayPrefsEnvelopeDto): DisplayPrefsEnvelopeDto

    @GET("me/settings/aliases")
    suspend fun getAliases(): AliasesEnvelopeDto

    @PUT("me/settings/aliases")
    suspend fun updateAliases(@Body body: AliasesEnvelopeDto): AliasesEnvelopeDto

    /** Server notification prefs (triggers + whitelists + muted_targets). GET falls
     * back to server defaults when the subject never saved any. PUT takes the FULL
     * map (hand-built JsonObject — a permanent mute is an explicit null `until`,
     * which AppJson's explicitNulls=false would drop from a data-class body, same
     * trap as [updateAutoAwayDebounce]). */
    @GET("me/settings/notification-prefs")
    suspend fun getNotificationPrefs(): NotificationPrefsEnvelopeDto

    @PUT("me/settings/notification-prefs")
    suspend fun updateNotificationPrefs(@Body body: JsonObject): NotificationPrefsEnvelopeDto

    /** Account-wide (not per-network) self-service vhost pick — see
     * `Grappa.Vhosts` moduledoc: an admin curates AVAILABILITY, the subject
     * SELECTS within it, and a per-network admin-pinned source (if any) still
     * overrides the selection at connect time regardless of what's picked here. */
    @GET("me/settings/vhost")
    suspend fun getVhostSettings(): VhostSettingsDto

    @PUT("me/settings/vhost")
    suspend fun updateVhostSelection(@Body body: VhostSelectionUpdateDto): VhostSettingsDto

    /** #348 — the auto-away grace period (`null` = site default, `0` = off, `N` = seconds). */
    @GET("me/settings/auto-away-debounce-seconds")
    suspend fun getAutoAwayDebounce(): AutoAwayDebounceDto

    /** Body is a hand-built [JsonObject] (not [AutoAwayDebounceDto]) because `null` here
     * is a real state — "clear to site default" — not "leave unchanged", and the shared
     * `AppJson` config's `explicitNulls = false` would drop the key for a normal
     * data-class body, silently turning that clear into a no-op PUT (same reasoning as
     * [pm.antani.resentin.net.rest.AdminApi.updateNetwork]). */
    @PUT("me/settings/auto-away-debounce-seconds")
    suspend fun updateAutoAwayDebounce(@Body body: JsonObject): AutoAwayDebounceDto

    /** M2 — the peer-avatar/gender-badge opt-in. See [ShowPeerProfilesDto]. */
    @GET("me/settings/show-peer-profiles")
    suspend fun getShowPeerProfiles(): ShowPeerProfilesDto

    @PUT("me/settings/show-peer-profiles")
    suspend fun updateShowPeerProfiles(@Body body: ShowPeerProfilesDto): ShowPeerProfilesDto

    /** Sezione IRC — messaggi PART/QUIT personalizzabili (vedi [IrcMessagesDto]).
     * Stessa infrastruttura server-side delle altre user settings (cross-device).
     * Il PUT prende un [JsonObject] hand-built (non [IrcMessagesDto]) perché `null`
     * qui è uno stato reale — "torna al predefinito" — non "lascia invariato", e la
     * config condivisa `AppJson` con `explicitNulls = false` dropperebbe la chiave
     * per un body data-class, trasformando silenziosamente quel reset in un no-op
     * (stesso motivo di [updateAutoAwayDebounce]). `""` è invece "nessun motivo". */
    @GET("me/settings/irc-messages")
    suspend fun getIrcMessages(): IrcMessagesDto

    @PUT("me/settings/irc-messages")
    suspend fun updateIrcMessages(@Body body: JsonObject): IrcMessagesDto
}
