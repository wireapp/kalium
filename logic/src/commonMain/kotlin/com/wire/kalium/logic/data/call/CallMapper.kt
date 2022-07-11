package com.wire.kalium.logic.data.call

import com.wire.kalium.calling.CallTypeCalling
import com.wire.kalium.calling.ConversationTypeCalling
import com.wire.kalium.calling.VideoStateCalling
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.call.CallEntity

class CallMapper {

    fun toCallTypeCalling(callType: CallType): CallTypeCalling {
        return when (callType) {
            CallType.AUDIO -> CallTypeCalling.AUDIO
            CallType.VIDEO -> CallTypeCalling.VIDEO
        }
    }

    fun toConversationTypeCalling(conversationType: ConversationType): ConversationTypeCalling {
        return when (conversationType) {
            ConversationType.OneOnOne -> ConversationTypeCalling.OneOnOne
            ConversationType.Conference -> ConversationTypeCalling.Conference
            else -> ConversationTypeCalling.Unknown
        }
    }

    fun fromIntToConversationType(conversationType: Int): ConversationType {
        return when (conversationType) {
            0 -> ConversationType.OneOnOne
            2 -> ConversationType.Conference
            else -> ConversationType.Unknown
        }
    }

    fun toVideoStateCalling(videoState: VideoState): VideoStateCalling {
        return when (videoState) {
            VideoState.STOPPED -> VideoStateCalling.STOPPED
            VideoState.STARTED -> VideoStateCalling.STARTED
            VideoState.BAD_CONNECTION -> VideoStateCalling.BAD_CONNECTION
            VideoState.PAUSED -> VideoStateCalling.PAUSED
            VideoState.SCREENSHARE -> VideoStateCalling.SCREENSHARE
        }
    }

    fun toCallEntity(
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

    fun toCall(
        callEntity: CallEntity,
        metadata: CallMetaData?
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

    private fun toConversationType(conversationType: ConversationEntity.Type): Conversation.Type = when (conversationType) {
        ConversationEntity.Type.GROUP -> Conversation.Type.GROUP
        else -> Conversation.Type.ONE_ON_ONE
    }

    fun toCallEntityStatus(callStatus: CallStatus): CallEntity.Status = when (callStatus) {
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

    fun fromConversationIdToQualifiedIDEntity(conversationId: ConversationId): QualifiedIDEntity = QualifiedIDEntity(
        value = conversationId.value,
        domain = conversationId.domain
    )

    val participantMapper = ParticipantMapper()
    val activeSpeakerMapper = ActiveSpeakerMapper()

    inner class ParticipantMapper {

        fun fromCallMemberToParticipant(member: CallMember): Participant = with(member) {
            Participant(
                id = QualifiedID(
                    value = userId.removeDomain(),
                    domain = userId.getDomain()
                ),
                clientId = clientId,
                isMuted = isMuted == 1
            )
        }

        fun fromCallMemberToCallClient(member: CallMember): CallClient = with(member) {
            CallClient(
                userId = QualifiedID(
                    value = userId.removeDomain(),
                    domain = userId.getDomain()
                ).toString(),
                clientId = clientId
            )
        }
    }

    inner class ActiveSpeakerMapper {
        fun mapParticipantsActiveSpeaker(
            participants: List<Participant>,
            activeSpeakers: CallActiveSpeakers
        ): List<Participant> = participants.map { participant ->
            participant.copy(
                isSpeaking = activeSpeakers.activeSpeakers.any {
                    it.userId == participant.id.toString() && it.clientId == participant.clientId
                }
            )
        }
    }

    private companion object {
        private const val DOMAIN_SEPARATOR = "@"

        private fun String.removeDomain() = if (contains(DOMAIN_SEPARATOR)) split(DOMAIN_SEPARATOR).first() else this

        private fun String.getDomain() = if (contains(DOMAIN_SEPARATOR)) split(DOMAIN_SEPARATOR).last() else ""
    }
}
