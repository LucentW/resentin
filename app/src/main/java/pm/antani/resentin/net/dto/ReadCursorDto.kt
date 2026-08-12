package pm.antani.resentin.net.dto

import kotlinx.serialization.Serializable

@Serializable
data class ReadCursorRequestDto(val messageId: Long)

@Serializable
data class ReadCursorResponseDto(val lastReadMessageId: Long)
