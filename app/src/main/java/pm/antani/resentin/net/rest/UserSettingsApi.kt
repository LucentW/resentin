package pm.antani.resentin.net.rest

import pm.antani.resentin.net.dto.AliasesEnvelopeDto
import pm.antani.resentin.net.dto.DisplayPrefsEnvelopeDto
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

    /** Account-wide (not per-network) self-service vhost pick — see
     * `Grappa.Vhosts` moduledoc: an admin curates AVAILABILITY, the subject
     * SELECTS within it, and a per-network admin-pinned source (if any) still
     * overrides the selection at connect time regardless of what's picked here. */
    @GET("me/settings/vhost")
    suspend fun getVhostSettings(): VhostSettingsDto

    @PUT("me/settings/vhost")
    suspend fun updateVhostSelection(@Body body: VhostSelectionUpdateDto): VhostSettingsDto
}
