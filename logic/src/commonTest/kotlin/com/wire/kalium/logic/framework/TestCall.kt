package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.call.CallMetadata
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.call.CallEntity

object TestCall {

    // Call Entity
    val DATABASE_ID = "abcd-1234"
    val CALLER_ID = UserId(
        value = "callerValue",
        domain = "callerDomain"
    )
    val CONVERSATION_ID = ConversationId(
        value = "convId",
        domain = "domainId"
    )

    fun qualifiedIdEntity(conversationId: ConversationId = CONVERSATION_ID) =
        QualifiedIDEntity(
            value = conversationId.value,
            domain = conversationId.domain
        )

    fun oneOnOneEstablishedCallEntity() = CallEntity(
        conversationId = qualifiedIdEntity(),
        id = DATABASE_ID,
        status = CallEntity.Status.ESTABLISHED,
        callerId = CALLER_ID.toString(),
        conversationType = ConversationEntity.Type.ONE_ON_ONE
    )

    // Call Metadata
    val CONVERSATION_NAME = "conv name"
    val CALLER_NAME = "caller name"
    val CALLER_TEAM_NAME = "caller team name"

    fun oneOnOneCallMetadata() = CallMetadata(
        isMuted = true,
        isCameraOn = false,
        conversationName = CONVERSATION_NAME,
        conversationType = Conversation.Type.ONE_ON_ONE,
        callerName = CALLER_NAME,
        callerTeamName = CALLER_TEAM_NAME,
        establishedTime = null
    )

    fun oneOnOneEstablishedCall() = Call(
        conversationId = CONVERSATION_ID,
        status = CallStatus.ESTABLISHED,
        isMuted = true,
        isCameraOn = false,
        callerId = CALLER_ID.toString(),
        conversationName = CONVERSATION_NAME,
        conversationType = Conversation.Type.ONE_ON_ONE,
        callerName = CALLER_NAME,
        callerTeamName = CALLER_TEAM_NAME,
        establishedTime = null,
        participants = emptyList(),
        maxParticipants = 0
    )

    fun oneOnOneIncomingCall(convId: ConversationId) =
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
