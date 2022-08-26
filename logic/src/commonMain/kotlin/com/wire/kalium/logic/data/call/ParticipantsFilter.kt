package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.user.UserId

interface ParticipantsFilter {
    fun participantsWithoutUserId(participants: List<Participant>, userId: UserId): List<Participant>
    fun selfParticipants(participants: List<Participant>, userId: UserId): List<Participant>
    fun participantsByCamera(participants: List<Participant>, isCameraOn: Boolean): List<Participant>
    fun participantsWithScreenSharingOn(participants: List<Participant>): List<Participant>
}

class ParticipantsFilterImpl : ParticipantsFilter {
    override fun participantsWithoutUserId(participants: List<Participant>, userId: UserId) = participants.filter { it.id != userId }
    override fun selfParticipants(participants: List<Participant>, userId: UserId) = participants.filter { it.id == userId }
    override fun participantsByCamera(participants: List<Participant>, isCameraOn: Boolean) = participants.filter { it.isCameraOn == isCameraOn }
    // TODO in next PR
    override fun participantsWithScreenSharingOn(participants: List<Participant>): List<Participant> = listOf()
}
