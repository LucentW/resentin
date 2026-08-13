package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class PushVapidKeyDto(
    val publicKey: String,
)

@Serializable
data class PushKeysDto(
    val p256dh: String,
    val auth: String,
)

@Serializable
data class PushSubscriptionRequestDto(
    val endpoint: String,
    val keys: PushKeysDto,
    val provider: String = "unifiedpush",
    /** Previous endpoint being replaced on re-subscribe, so the server can prune the
     * ghost row atomically instead of leaving two live rows for the same device. */
    val supersedes: String? = null,
)

@Serializable
data class PushSubscriptionCreatedDto(
    val id: String,
    val createdAt: String,
)

@Serializable
data class PushSubscriptionSummaryDto(
    val id: String,
    // Defaults to "webpush" for a server that predates the `:provider` column (grappa-irc
    // PR #1261, still in draft) — every subscription that server can list was
    // necessarily a browser Web Push registration anyway, so this is the correct fallback,
    // not a guess.
    val provider: String = "webpush",
    val userAgent: String? = null,
    val createdAt: String,
    val lastUsedAt: String? = null,
)

@Serializable
data class PushSubscriptionsEnvelopeDto(
    val subscriptions: List<PushSubscriptionSummaryDto>,
)

/** Shape of the ALREADY-DECRYPTED push message content — see `Grappa.Push.Payload`.
 * Deliberately English-only, server-picked strings (the OS notification surface renders
 * `title`/`body` before this client's own localization ever gets a chance); this client
 * uses `url` to route to the conversation and re-derives the actual notification text
 * from a REST backfill instead of trusting `title`/`body` verbatim. */
@Serializable
data class PushNotificationPayloadDto(
    val title: String,
    val body: String,
    val tag: String,
    val url: String,
    val badge: Int? = null,
)
