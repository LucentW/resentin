package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

/** `dcc_offer` user-topic event (issue 2089 on grappa-irc) — a peer offered a file over
 * `DCC SEND` and the bouncer is HOLDING it, awaiting consent. Same shape the
 * `GET /networks/:slug/dcc_offers` cold-start list returns per entry (see
 * `Grappa.Session.Wire.dcc_offer/6`). `filename` is already the neutralised display
 * name, `size` is the peer's unverified claim. `channel` is where to render the
 * consent banner (a stranger's offer routes to `$server`), not part of the offer
 * itself. */
@Serializable
data class DccOfferDto(
    val network: String,
    val channel: String,
    val offerId: String,
    val from: String,
    val filename: String,
    val size: Long,
)

/** `dcc_offer_resolved` — the held offer left the held set; every device must drop its
 * banner regardless of how (`resolution` is display-only, ignored here — the client
 * reaction is the same in all three cases). */
@Serializable
data class DccOfferResolvedDto(
    val network: String,
    val channel: String,
    val offerId: String,
)
