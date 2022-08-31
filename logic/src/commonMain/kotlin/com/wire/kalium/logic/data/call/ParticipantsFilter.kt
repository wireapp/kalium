package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.user.UserId

interface ParticipantsFilter {
    fun participantsWithoutUserId(participants: List<Participant>, userId: UserId): List<Participant>
    fun selfParticipants(participants: List<Participant>, userId: UserId): List<Participant>
    fun participantsByCamera(participants: List<Participant>, isCameraOn: Boolean): List<Participant>
    fun participantsSharingScreen(participants: List<Participant>, isSharingScreen: Boolean): List<Participant>
}

class ParticipantsFilterImpl(val qualifiedIdMapper: QualifiedIdMapper) : ParticipantsFilter {
    override fun participantsWithoutUserId(participants: List<Participant>, userId: UserId) = participants.filter {
        qualifiedIdMapper.fromStringToQualifiedID(it.id.toString()) != userId
    }

    override fun selfParticipants(participants: List<Participant>, userId: UserId) = participants.filter {
        qualifiedIdMapper.fromStringToQualifiedID(it.id.toString()) == userId
    }

    override fun participantsByCamera(participants: List<Participant>, isCameraOn: Boolean) = participants.filter {
        it.isCameraOn == isCameraOn
    }

    override fun participantsSharingScreen(participants: List<Participant>, isSharingScreen: Boolean) = participants.filter {
        it.isSharingScreen == isSharingScreen
    }
}
