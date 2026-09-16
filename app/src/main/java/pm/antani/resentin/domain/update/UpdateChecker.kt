package pm.antani.resentin.domain.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pm.antani.resentin.BuildConfig
import pm.antani.resentin.net.HttpClients
import pm.antani.resentin.net.rest.GitHubApi

data class AvailableUpdate(val version: String, val releaseUrl: String)

/**
 * Checks GitHub Releases for a build newer than this one. Only meaningful for the
 * sideloaded/GitHub-distributed build — [BuildConfig.UPDATE_CHECK_ENABLED] is false
 * on any future Play Store (or otherwise store-distributed) flavor, which handles
 * its own updates, so [check] no-ops there without ever calling out.
 */
class UpdateChecker {

    private val _available = MutableStateFlow<AvailableUpdate?>(null)
    val available: StateFlow<AvailableUpdate?> = _available.asStateFlow()

    suspend fun check() {
        if (!BuildConfig.UPDATE_CHECK_ENABLED) return
        runCatching {
            val retrofit = HttpClients.retrofit("api.github.com", HttpClients.okHttpClient())
            retrofit.create(GitHubApi::class.java).latestRelease()
        }.onSuccess { release ->
            if (release.draft || release.prerelease) return@onSuccess
            val latest = release.tagName.removePrefix("v")
            if (isNewer(latest, BuildConfig.VERSION_NAME)) {
                _available.value = AvailableUpdate(latest, release.htmlUrl)
            }
        }
    }
}

private fun isNewer(latest: String, current: String): Boolean {
    val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
    val c = current.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(l.size, c.size)) {
        val a = l.getOrElse(i) { 0 }
        val b = c.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}
