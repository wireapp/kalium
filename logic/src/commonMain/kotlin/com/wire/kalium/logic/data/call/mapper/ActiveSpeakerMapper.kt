package com.wire.kalium.logic.data.call.mapper

import com.wire.kalium.logic.data.call.CallActiveSpeakers
import com.wire.kalium.logic.data.call.Participant

interface ActiveSpeakerMapper {
    fun mapParticipantsActiveSpeaker(
        participants: List<Participant>,
        activeSpeakers: CallActiveSpeakers
    ): List<Participant>
}

class ActiveSpeakerMapperImpl : ActiveSpeakerMapper {
    override fun mapParticipantsActiveSpeaker(
        participants: List<Participant>,
        activeSpeakers: CallActiveSpeakers
    ): List<Participant> = participants.map { participant ->
        val isSpeaking = activeSpeakers.activeSpeakers.find {
            it.userId == participant.id.toString() && it.clientId == participant.clientId
        }?.let {
            it.audioLevel > 0 && it.audioLevelNow > 0
        } ?: run { false }
        participant.copy(
            isSpeaking = isSpeaking
        )
    }
}
