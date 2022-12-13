package com.wire.kalium.network.api.base.authenticated.conversation

import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO

sealed class UpdateConversationAccessResponse {
    object AccessUnchanged : UpdateConversationAccessResponse()
    data class AccessUpdated(val event: EventContentDTO.Conversation.AccessUpdate) : UpdateConversationAccessResponse()
}
