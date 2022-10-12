package com.wire.kalium.logic.data.call.mapper

import com.wire.kalium.calling.CallTypeCalling
import com.wire.kalium.calling.ConversationTypeCalling
import com.wire.kalium.calling.VideoStateCalling
import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.call.CallMetadata
import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.call.ConversationType
import com.wire.kalium.logic.data.call.VideoState
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.feature.message.MessageTarget
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.call.CallEntity

interface CallMapper {
    fun toCallTypeCalling(callType: CallType): CallTypeCalling
    fun toConversationTypeCalling(conversationType: ConversationType): ConversationTypeCalling
    fun fromIntToConversationType(conversationType: Int): ConversationType
    fun fromIntToCallingVideoState(videStateInt: Int): VideoStateCalling
    fun toVideoStateCalling(videoState: VideoState): VideoStateCalling
    fun toCallEntity(
        conversationId: ConversationId,
        id: String,
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

class CallMapperImpl(
    private val qualifiedIdMapper: QualifiedIdMapper
) : CallMapper {

    override fun toCallTypeCalling(callType: CallType): CallTypeCalling {
        return when (callType) {
            CallType.AUDIO -> CallTypeCalling.AUDIO
            CallType.VIDEO -> CallTypeCalling.VIDEO
        }
    }

    override fun toConversationTypeCalling(conversationType: ConversationType): ConversationTypeCalling {
        return when (conversationType) {
            ConversationType.OneOnOne -> ConversationTypeCalling.OneOnOne
            ConversationType.Conference -> ConversationTypeCalling.Conference
            else -> ConversationTypeCalling.Unknown
        }
    }

    override fun fromIntToConversationType(conversationType: Int): ConversationType {
        return when (conversationType) {
            0 -> ConversationType.OneOnOne
            2 -> ConversationType.Conference
            else -> ConversationType.Unknown
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

    override fun toCallEntity(
        conversationId: ConversationId,
        id: String,
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
        conversationType = toConversationEntityType(conversationType = conversationType)
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
        callerId = callEntity.callerId,
        conversationName = metadata?.conversationName,
        conversationType = toConversationType(conversationType = callEntity.conversationType),
        callerName = metadata?.callerName,
        callerTeamName = metadata?.callerTeamName,
        establishedTime = metadata?.establishedTime,
        participants = metadata?.participants ?: emptyList(),
        maxParticipants = metadata?.maxParticipants ?: 0
    )

    private fun toConversationEntityType(conversationType: Conversation.Type): ConversationEntity.Type = when (conversationType) {
        Conversation.Type.GROUP -> ConversationEntity.Type.GROUP
        else -> ConversationEntity.Type.ONE_ON_ONE
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
        CallStatus.CLOSED -> CallEntity.Status.CLOSED
    }

    private fun toCallStatus(callStatus: CallEntity.Status): CallStatus = when (callStatus) {
        CallEntity.Status.STARTED -> CallStatus.STARTED
        CallEntity.Status.INCOMING -> CallStatus.INCOMING
        CallEntity.Status.MISSED -> CallStatus.MISSED
        CallEntity.Status.ANSWERED -> CallStatus.ANSWERED
        CallEntity.Status.ESTABLISHED -> CallStatus.ESTABLISHED
        CallEntity.Status.STILL_ONGOING -> CallStatus.STILL_ONGOING
        CallEntity.Status.CLOSED -> CallStatus.CLOSED
    }

    override fun fromConversationIdToQualifiedIDEntity(conversationId: ConversationId): QualifiedIDEntity = QualifiedIDEntity(
        value = conversationId.value,
        domain = conversationId.domain
    )

    override fun toClientMessageTarget(callClientList: CallClientList): MessageTarget.Client {
        val recipientsList = mutableListOf<Recipient>()

        for (callClient in callClientList.clients) {
            val qualifiedUserId = qualifiedIdMapper.fromStringToQualifiedID(callClient.userId)
            val clientId = ClientId(callClient.clientId)
            val recipientIndex = recipientsList.indexOfFirst { it.id == qualifiedUserId }

            if (recipientIndex == -1) {
                recipientsList.add(
                    Recipient(
                        id = qualifiedUserId,
                        clients = mutableListOf(clientId)
                    )
                )
            } else {
                recipientsList[recipientIndex] = recipientsList[recipientIndex].copy(
                    clients = recipientsList[recipientIndex].clients.toMutableList().apply {
                        add(clientId)
                    }
                )
            }
        }

        return MessageTarget.Client(
            recipients = recipientsList
        )
    }
}
