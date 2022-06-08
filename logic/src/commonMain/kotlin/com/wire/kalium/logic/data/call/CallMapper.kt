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

    private val DOMAIN_SEPARATOR = "@"

    private fun String.removeDomain() = if (contains(DOMAIN_SEPARATOR)) split(DOMAIN_SEPARATOR).first() else this

    private fun String.getDomain() = if (contains(DOMAIN_SEPARATOR)) split(DOMAIN_SEPARATOR).last() else ""

    val participantMapper = ParticipantMapper()
    val activeSpeakerMapper = ActiveSpeakerMapper()

    inner class ParticipantMapper {

        fun fromCallMemberToParticipant(member: CallMember): Participant = with(member) {
            Participant(
                id = QualifiedID(
                    value = userid.removeDomain(),
                    domain = userid.getDomain()
                ),
                clientId = clientid,
                muted = muted == 1
            )
        }

        fun fromCallMemberToCallClient(member: CallMember): CallClient = with(member) {
            CallClient(
                userId = QualifiedID(
                    value = userid.removeDomain(),
                    domain = userid.getDomain()
                ).toString(),
                clientId = clientid
            )
        }
    }

    inner class ActiveSpeakerMapper {

        fun fromCallActiveSpeakerToActiveSpeaker(callActiveSpeaker: CallActiveSpeaker): ActiveSpeaker = with(callActiveSpeaker) {
            ActiveSpeaker(
                userId = userId.removeDomain(),
                clientId = clientId,
                audioLevel = audioLevel,
                audioLevelNow = audioLevelNow
            )
        }
    }
}
