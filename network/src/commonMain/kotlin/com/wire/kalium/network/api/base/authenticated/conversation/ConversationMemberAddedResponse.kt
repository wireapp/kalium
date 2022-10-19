package com.wire.kalium.network.api.base.authenticated.conversation

import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO

sealed class ConversationMemberAddedResponse {
    /**
     * The users requested to be added were already members
     */
    object Unchanged : ConversationMemberAddedResponse()

    data class Changed(val event: EventContentDTO.Conversation.MemberJoinDTO) : ConversationMemberAddedResponse()
}
