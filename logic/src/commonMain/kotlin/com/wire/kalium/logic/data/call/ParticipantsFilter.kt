package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.user.UserId

interface ParticipantsFilter {
    fun otherParticipants(participants: List<Participant>, clientId: String): List<Participant>
    fun selfParticipant(participants: List<Participant>, userId: UserId, clientId: String): Participant
    fun participantsByCamera(participants: List<Participant>, isCameraOn: Boolean): List<Participant>
    fun participantsSharingScreen(participants: List<Participant>, isSharingScreen: Boolean): List<Participant>
}

class ParticipantsFilterImpl(val qualifiedIdMapper: QualifiedIdMapper) : ParticipantsFilter {
    override fun otherParticipants(participants: List<Participant>, clientId: String) = participants.filter {
        it.clientId != clientId
    }

    override fun selfParticipant(participants: List<Participant>, userId: UserId, clientId: String) = participants.first {
        qualifiedIdMapper.fromStringToQualifiedID(it.id.toString()) == userId && it.clientId == clientId
    }

    override fun participantsByCamera(participants: List<Participant>, isCameraOn: Boolean) = participants.filter {
        it.isCameraOn == isCameraOn
    }

    override fun participantsSharingScreen(participants: List<Participant>, isSharingScreen: Boolean) = participants.filter {
        it.isSharingScreen == isSharingScreen
    }
}
