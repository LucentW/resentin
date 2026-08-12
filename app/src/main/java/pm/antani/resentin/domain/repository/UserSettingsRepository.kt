package pm.antani.resentin.domain.repository

import pm.antani.resentin.net.dto.AliasesEnvelopeDto
import pm.antani.resentin.net.dto.DisplayPrefsDto
import pm.antani.resentin.net.dto.DisplayPrefsEnvelopeDto
import pm.antani.resentin.net.rest.UserSettingsApi

class UserSettingsRepository(private val authRepository: AuthRepository) {

    suspend fun getDisplayPrefs(): Result<DisplayPrefsDto> = runCatching {
        authRepository.api(UserSettingsApi::class.java).getDisplayPrefs().displayPrefs
    }

    suspend fun updateDisplayPrefs(prefs: DisplayPrefsDto): Result<DisplayPrefsDto> = runCatching {
        authRepository.api(UserSettingsApi::class.java)
            .updateDisplayPrefs(DisplayPrefsEnvelopeDto(prefs)).displayPrefs
    }

    suspend fun getAliases(): Result<Map<String, String>> = runCatching {
        authRepository.api(UserSettingsApi::class.java).getAliases().aliases
    }

    suspend fun updateAliases(aliases: Map<String, String>): Result<Map<String, String>> = runCatching {
        authRepository.api(UserSettingsApi::class.java).updateAliases(AliasesEnvelopeDto(aliases)).aliases
    }
}
