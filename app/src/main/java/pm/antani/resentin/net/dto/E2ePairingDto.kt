package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

/** A pairing rendezvous (CLIENT_PROTOCOL §4g). role: creator | joiner. */
@Serializable
data class PairingRendezvousDto(val id: String, val role: String, val expiresAt: String? = null)

@Serializable
data class PairingRendezvousResponseDto(val rendezvous: PairingRendezvousDto)

@Serializable
data class PairingOpenDto(val curve: String)

@Serializable
data class PairingOfferDto(val rendezvousId: String, val curve: String, val expiresAt: String)

@Serializable
data class PairingOfferResponseDto(val offer: PairingOfferDto? = null)

@Serializable
data class PairingPostDto(val payload: String)

@Serializable
data class PairingMessageDto(val seq: Int, val from: String, val payload: String)

@Serializable
data class PairingMessageResponseDto(val message: PairingMessageDto)

@Serializable
data class PairingMessagesDto(val messages: List<PairingMessageDto> = emptyList())
