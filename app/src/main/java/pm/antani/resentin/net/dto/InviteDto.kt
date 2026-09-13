package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

/** `window_invited` user-topic event — an inbound IRC INVITE the operator hasn't
 * acted on yet. `network` is the slug (see `Grappa.Session.Wire.window_invited/3`),
 * matching every other user-topic event's convention. */
@Serializable
data class WindowInvitedDto(
    val network: String,
    val channel: String,
    val inviter: String,
)

/** `window_invite_declined` — the invite for [channel] was refused (by this device
 * or another one on the same account) and its banner must be dropped everywhere. */
@Serializable
data class WindowInviteDeclinedDto(
    val network: String,
    val channel: String,
)
