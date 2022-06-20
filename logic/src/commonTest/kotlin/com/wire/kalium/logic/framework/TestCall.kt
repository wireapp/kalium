package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus

object TestCall {

    fun onOnOneIncomingCall(convId: ConversationId) =
        Call(
            convId,
            CallStatus.INCOMING,
            false,
            false,
            "client1",
            "ONE_ON_ONE Name ${convId.value}",
            Conversation.Type.ONE_ON_ONE,
            null,
            null
        )

    fun groupIncomingCall(convId: ConversationId) =
        Call(
            convId,
            CallStatus.INCOMING,
            false,
            false,
            "client1",
            "ONE_ON_ONE Name ${convId.value}",
            Conversation.Type.GROUP,
            null,
            null
        )
}
