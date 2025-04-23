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

package com.wire.kalium.logic.data.call.mapper

import com.wire.kalium.calling.CallTypeCalling
import com.wire.kalium.calling.ConversationTypeCalling
import com.wire.kalium.calling.VideoStateCalling
import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.call.CallMetadata
import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.call.ConversationTypeForCall
import com.wire.kalium.logic.data.call.VideoState
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.message.MessageTarget
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.call.CallEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import io.mockative.Mockable

@Mockable
interface CallMapper {
    fun toCallTypeCalling(callType: CallType): CallTypeCalling
    fun toConversationTypeCalling(conversationTypeForCall: ConversationTypeForCall): ConversationTypeCalling
    fun toConversationType(conversationTypeCalling: ConversationTypeCalling): ConversationTypeForCall
    fun fromIntToConversationType(conversationType: Int): ConversationTypeForCall
    fun fromIntToCallingVideoState(videStateInt: Int): VideoStateCalling
    fun toVideoStateCalling(videoState: VideoState): VideoStateCalling
    fun fromConversationTypeToConversationTypeForCall(
        conversationType: Conversation.Type,
        conversationProtocol: Conversation.ProtocolInfo
    ): ConversationTypeForCall

    @Suppress("LongParameterList")
    fun toCallEntity(
        conversationId: ConversationId,
        id: String,
        type: ConversationTypeForCall,
        status: CallStatus,
        conversationType: Conversation.Type,
        callerId: UserId
    ): CallEntity

    fun toCall(
        callEntity: CallEntity,
        metadata: CallMetadata?
    ): Call

    fun toConversationType(conversationType: ConversationEntity.Type): Conversation.Type
    fun toCallEntityStatus(callStatus: CallStatus): CallEntity.Status
    fun fromConversationIdToQualifiedIDEntity(conversationId: ConversationId): QualifiedIDEntity

    fun toClientMessageTarget(callClientList: CallClientList): MessageTarget.Client
}

@Suppress("TooManyFunctions")
class CallMapperImpl(
    private val qualifiedIdMapper: QualifiedIdMapper
) : CallMapper {

    override fun toCallTypeCalling(callType: CallType): CallTypeCalling {
        return when (callType) {
            CallType.AUDIO -> CallTypeCalling.AUDIO
            CallType.VIDEO -> CallTypeCalling.VIDEO
        }
    }

    override fun toConversationTypeCalling(conversationTypeForCall: ConversationTypeForCall): ConversationTypeCalling {
        return when (conversationTypeForCall) {
            ConversationTypeForCall.OneOnOne -> ConversationTypeCalling.OneOnOne
            ConversationTypeForCall.Conference -> ConversationTypeCalling.Conference
            ConversationTypeForCall.ConferenceMls -> ConversationTypeCalling.ConferenceMls
            else -> ConversationTypeCalling.Unknown
        }
    }

    override fun toConversationType(conversationTypeCalling: ConversationTypeCalling): ConversationTypeForCall {
        return when (conversationTypeCalling) {
            ConversationTypeCalling.OneOnOne -> ConversationTypeForCall.OneOnOne
            ConversationTypeCalling.Conference -> ConversationTypeForCall.Conference
            ConversationTypeCalling.ConferenceMls -> ConversationTypeForCall.ConferenceMls
            else -> ConversationTypeForCall.Unknown
        }
    }

    override fun fromIntToConversationType(conversationType: Int): ConversationTypeForCall {
        return when (conversationType) {
            ConversationTypeCalling.OneOnOne.avsValue -> ConversationTypeForCall.OneOnOne
            ConversationTypeCalling.Conference.avsValue -> ConversationTypeForCall.Conference
            ConversationTypeCalling.ConferenceMls.avsValue -> ConversationTypeForCall.ConferenceMls
            else -> ConversationTypeForCall.Unknown
        }
    }

    @Suppress("MagicNumber")
    override fun fromIntToCallingVideoState(videStateInt: Int): VideoStateCalling {
        return when (videStateInt) {
            0 -> VideoStateCalling.STOPPED
            1 -> VideoStateCalling.STARTED
            2 -> VideoStateCalling.BAD_CONNECTION
            3 -> VideoStateCalling.PAUSED
            4 -> VideoStateCalling.SCREENSHARE
            else -> VideoStateCalling.UNKNOWN
        }
    }

    override fun toVideoStateCalling(videoState: VideoState) = when (videoState) {
        VideoState.STOPPED -> VideoStateCalling.STOPPED
        VideoState.STARTED -> VideoStateCalling.STARTED
        VideoState.BAD_CONNECTION -> VideoStateCalling.BAD_CONNECTION
        VideoState.PAUSED -> VideoStateCalling.PAUSED
        VideoState.SCREENSHARE -> VideoStateCalling.SCREENSHARE
        VideoState.UNKNOWN -> VideoStateCalling.UNKNOWN
    }

    override fun fromConversationTypeToConversationTypeForCall(
        conversationType: Conversation.Type,
        conversationProtocol: Conversation.ProtocolInfo
    ): ConversationTypeForCall =
        when (conversationType) {
            Conversation.Type.GROUP -> {
                when (conversationProtocol) {
                    is Conversation.ProtocolInfo.MLS -> ConversationTypeForCall.ConferenceMls
                    is Conversation.ProtocolInfo.Proteus,
                    is Conversation.ProtocolInfo.Mixed -> ConversationTypeForCall.Conference
                }
            }
            Conversation.Type.ONE_ON_ONE -> ConversationTypeForCall.OneOnOne
            else -> ConversationTypeForCall.Unknown
        }

    override fun toCallEntity(
        conversationId: ConversationId,
        id: String,
        type: ConversationTypeForCall,
        status: CallStatus,
        conversationType: Conversation.Type,
        callerId: UserId
    ): CallEntity = CallEntity(
        conversationId = QualifiedIDEntity(
            value = conversationId.value,
            domain = conversationId.domain
        ),
        id = id,
        status = toCallEntityStatus(callStatus = status),
        callerId = callerId.toString(),
        conversationType = toConversationEntityType(conversationType = conversationType),
        type = toCallEntityType(type)
    )

    override fun toCall(
        callEntity: CallEntity,
        metadata: CallMetadata?
    ): Call = Call(
        conversationId = ConversationId(
            value = callEntity.conversationId.value,
            domain = callEntity.conversationId.domain
        ),
        status = toCallStatus(callStatus = callEntity.status),
        isMuted = metadata?.isMuted ?: true,
        isCameraOn = metadata?.isCameraOn ?: false,
        isCbrEnabled = metadata?.isCbrEnabled ?: false,
        callerId = metadata?.callerId ?: qualifiedIdMapper.fromStringToQualifiedID(callEntity.callerId),
        conversationName = metadata?.conversationName,
        conversationType = toConversationType(conversationType = callEntity.conversationType),
        callerName = metadata?.callerName,
        callerTeamName = metadata?.callerTeamName,
        establishedTime = metadata?.establishedTime,
        participants = metadata?.getFullParticipants() ?: emptyList(),
        maxParticipants = metadata?.maxParticipants ?: 0
    )

    private fun toConversationEntityType(conversationType: Conversation.Type): ConversationEntity.Type = when (conversationType) {
        Conversation.Type.GROUP -> ConversationEntity.Type.GROUP
        else -> ConversationEntity.Type.ONE_ON_ONE
    }

    private fun toCallEntityType(conversationTypeForCall: ConversationTypeForCall): CallEntity.Type = when (conversationTypeForCall) {
        ConversationTypeForCall.OneOnOne -> CallEntity.Type.ONE_ON_ONE
        ConversationTypeForCall.Conference -> CallEntity.Type.CONFERENCE
        ConversationTypeForCall.ConferenceMls -> CallEntity.Type.MLS_CONFERENCE
        ConversationTypeForCall.Unknown -> CallEntity.Type.UNKNOWN
    }

    override fun toConversationType(conversationType: ConversationEntity.Type): Conversation.Type = when (conversationType) {
        ConversationEntity.Type.GROUP -> Conversation.Type.GROUP
        else -> Conversation.Type.ONE_ON_ONE
    }

    override fun toCallEntityStatus(callStatus: CallStatus): CallEntity.Status = when (callStatus) {
        CallStatus.STARTED -> CallEntity.Status.STARTED
        CallStatus.INCOMING -> CallEntity.Status.INCOMING
        CallStatus.MISSED -> CallEntity.Status.MISSED
        CallStatus.ANSWERED -> CallEntity.Status.ANSWERED
        CallStatus.ESTABLISHED -> CallEntity.Status.ESTABLISHED
        CallStatus.STILL_ONGOING -> CallEntity.Status.STILL_ONGOING
        CallStatus.CLOSED_INTERNALLY -> CallEntity.Status.CLOSED_INTERNALLY
        CallStatus.CLOSED -> CallEntity.Status.CLOSED
        CallStatus.REJECTED -> CallEntity.Status.REJECTED
    }

    private fun toCallStatus(callStatus: CallEntity.Status): CallStatus = when (callStatus) {
        CallEntity.Status.STARTED -> CallStatus.STARTED
        CallEntity.Status.INCOMING -> CallStatus.INCOMING
        CallEntity.Status.MISSED -> CallStatus.MISSED
        CallEntity.Status.ANSWERED -> CallStatus.ANSWERED
        CallEntity.Status.ESTABLISHED -> CallStatus.ESTABLISHED
        CallEntity.Status.STILL_ONGOING -> CallStatus.STILL_ONGOING
        CallEntity.Status.CLOSED_INTERNALLY -> CallStatus.CLOSED_INTERNALLY
        CallEntity.Status.CLOSED -> CallStatus.CLOSED
        CallEntity.Status.REJECTED -> CallStatus.REJECTED
    }

    override fun fromConversationIdToQualifiedIDEntity(conversationId: ConversationId): QualifiedIDEntity = QualifiedIDEntity(
        value = conversationId.value,
        domain = conversationId.domain
    )

    override fun toClientMessageTarget(callClientList: CallClientList): MessageTarget.Client {
        val recipients = callClientList.clients.groupingBy { it.userId }
            .fold({ key, _ -> key to mutableListOf<ClientId>() }, { _, accumulator, element ->
                accumulator.also { (_, list) -> list.add(ClientId(element.clientId)) }
            }).run {
                values.toList().map {
                    val qualifiedUserId = qualifiedIdMapper.fromStringToQualifiedID(it.first)
                    Recipient(qualifiedUserId, it.second)
                }
            }

        return MessageTarget.Client(
            recipients = recipients
        )
    }
}
