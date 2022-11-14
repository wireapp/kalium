package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.LegalHoldStatus
import com.wire.kalium.logic.data.user.type.UserType

object TestConversationDetails {

    val CONNECTION = ConversationDetails.Connection(
        TestConversation.ID,
        TestUser.OTHER,
        UserType.EXTERNAL,
        "2022-03-30T15:36:00.000Z",
        TestConnection.CONNECTION,
        protocolInfo = ProtocolInfo.Proteus,
        access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
        accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST)
    )

    val CONVERSATION_ONE_ONE = ConversationDetails.OneOne(
        TestConversation.ONE_ON_ONE,
        TestUser.OTHER,
        LegalHoldStatus.DISABLED,
        UserType.EXTERNAL,
        unreadMessagesCount = 0,
        lastMessage = null,
        unreadContentCount = emptyMap()
    )

    val CONVERSATION_GROUP = ConversationDetails.Group(
        conversation = TestConversation.GROUP(),
        unreadMessagesCount = 0,
        legalHoldStatus = LegalHoldStatus.ENABLED,
        lastMessage = null,
        isSelfUserCreator = true,
        isSelfUserMember = true,
        unreadContentCount = emptyMap()
    )

}
