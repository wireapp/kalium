package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.LegalHoldStatus
import com.wire.kalium.logic.data.conversation.ProtocolInfo
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.type.UserType

object TestConversationDetails {

    val CONNECTION = ConversationDetails.Connection(
        TestConversation.ID,
        TestUser.OTHER,
        UserType.EXTERNAL,
        "2022-03-30T15:36:00.000Z",
        TestConnection.CONNECTION,
        protocolInfo = ProtocolInfo.Proteus
    )

    val CONVERSATION_ONE_ONE = ConversationDetails.OneOne(
        TestConversation.ONE_ON_ONE,
        TestUser.OTHER,
        ConnectionState.ACCEPTED,
        LegalHoldStatus.DISABLED,
        UserType.EXTERNAL
    )

}
