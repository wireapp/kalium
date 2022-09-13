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
    ): List<Participant> = participants.toMutableList().apply {
        activeSpeakers.activeSpeakers.forEach { activeSpeaker ->
            find { participant ->
                activeSpeaker.userId == participant.id.toString() && activeSpeaker.clientId == participant.clientId
            }?.let {
                this[indexOf(it)] = it.copy(
                    isSpeaking = activeSpeaker.audioLevel > 0 && activeSpeaker.audioLevelNow > 0
                )
            }
        }
    }
}
