package com.wire.kalium.network.api.base.authenticated.conversation

import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import kotlinx.serialization.Serializable

@Serializable
sealed class ConversationRenameResponse {

    object Unchanged : ConversationRenameResponse()

    data class Changed(val event: EventContentDTO.Conversation.ConversationRenameDTO) : ConversationRenameResponse()
}
