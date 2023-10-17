package com.wire.kalium.network.api.base.authenticated.conversation

import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO

sealed class UpdateConversationReceiptModeResponse {
    data object ReceiptModeUnchanged : UpdateConversationReceiptModeResponse()
    data class ReceiptModeUpdated(val event: EventContentDTO.Conversation.ReceiptModeUpdate) : UpdateConversationReceiptModeResponse()
}
