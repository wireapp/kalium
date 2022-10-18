package com.wire.kalium.network.api.base.authenticated.conversation

import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO

sealed class ConversationMemberRemovedResponse {

    /**
     * The users requested to be removed were not members
     */
    object Unchanged : ConversationMemberRemovedResponse()

    data class Changed(val event: EventContentDTO.Conversation.MemberLeaveDTO) : ConversationMemberRemovedResponse()

}
