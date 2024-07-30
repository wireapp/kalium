/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallMetadata
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.call.Participant
import com.wire.kalium.logic.data.call.ParticipantMinimized
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.OtherUserMinimized
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.call.CallEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity

object TestCall {

    // Call Entity
    const val DATABASE_ID = "abcd-1234"
    val CALLER_ID = UserId(
        value = "callerValue",
        domain = "callerDomain"
    )
    val CONVERSATION_ID = ConversationId(
        value = "convId",
        domain = "domainId"
    )
    const val CLIENT_ID_1 = "clientId1"
    const val CLIENT_ID_2 = "clientId2"

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
        conversationType = ConversationEntity.Type.ONE_ON_ONE,
        type = CallEntity.Type.ONE_ON_ONE
    )

    // Call Metadata
    const val CONVERSATION_NAME = "conv name"
    const val CALLER_NAME = "caller name"
    const val CALLER_TEAM_NAME = "caller team name"

    fun oneOnOneCallMetadata() = CallMetadata(
        isMuted = true,
        isCameraOn = false,
        isCbrEnabled = false,
        conversationName = CONVERSATION_NAME,
        conversationType = Conversation.Type.ONE_ON_ONE,
        callerName = CALLER_NAME,
        callerTeamName = CALLER_TEAM_NAME,
        establishedTime = null,
        callStatus = CallStatus.ESTABLISHED,
        protocol = Conversation.ProtocolInfo.Proteus,
        participants = listOf(
            ParticipantMinimized(
                id = QualifiedID("participantId", "participantDomain"),
                clientId = "abcd",
                isMuted = true,
                isCameraOn = false,
                isSharingScreen = false,
                hasEstablishedAudio = true
            )
        ),
        activeSpeakers = mapOf(QualifiedID("participantId", "participantDomain") to listOf("abcd")),
        users = listOf(
            OtherUserMinimized(
                id = QualifiedID("participantId", "participantDomain"),
                name = "User Name",
                completePicture = null,
                userType = UserType.ADMIN
            )
        )
    )

    fun oneOnOneEstablishedCall() = Call(
        conversationId = CONVERSATION_ID,
        status = CallStatus.ESTABLISHED,
        isMuted = true,
        isCameraOn = false,
        isCbrEnabled = false,
        callerId = CALLER_ID.toString(),
        conversationName = CONVERSATION_NAME,
        conversationType = Conversation.Type.ONE_ON_ONE,
        callerName = CALLER_NAME,
        callerTeamName = CALLER_TEAM_NAME,
        establishedTime = null,
        participants = listOf(
            Participant(
                id = QualifiedID("participantId", "participantDomain"),
                clientId = "abcd",
                isMuted = true,
                isCameraOn = false,
                isSharingScreen = false,
                hasEstablishedAudio = true,
                name = "User Name",
                avatarAssetId = null,
                userType = UserType.ADMIN,
                isSpeaking = false
            )
        ),
        maxParticipants = 0
    )

    fun oneOnOneIncomingCall(convId: ConversationId) =
        Call(
            convId,
            CallStatus.INCOMING,
            false,
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
            false,
            "client1",
            "ONE_ON_ONE Name ${convId.value}",
            Conversation.Type.GROUP,
            null,
            null
        )
}
