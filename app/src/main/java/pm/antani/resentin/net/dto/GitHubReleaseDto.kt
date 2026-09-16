package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

/** Minimal shape of GitHub's `GET /repos/:owner/:repo/releases/latest` response —
 * see [pm.antani.resentin.domain.update.UpdateChecker]. */
@Serializable
data class GitHubReleaseDto(
    val tagName: String,
    val htmlUrl: String,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
)
