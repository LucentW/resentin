package pm.antani.resentin.net.rest

import pm.antani.resentin.net.dto.AliasesEnvelopeDto
import pm.antani.resentin.net.dto.DisplayPrefsEnvelopeDto
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
}
