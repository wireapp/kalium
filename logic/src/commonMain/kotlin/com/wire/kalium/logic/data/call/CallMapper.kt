package com.wire.kalium.logic.data.call

import com.wire.kalium.calling.CallTypeCalling
import com.wire.kalium.calling.ConversationTypeCalling
import com.wire.kalium.calling.VideoStateCalling
import com.wire.kalium.logic.data.id.QualifiedID

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
        ) : List<Participant> = participants.map { participant ->
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
