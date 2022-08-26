package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.user.UserRepository

interface CallingParticipantsOrder {
    fun reorderItems(participants: List<Participant>): List<Participant>
}

class CallingParticipantsOrderImpl(
    private val userRepository: UserRepository,
    private val participantsFilter: ParticipantsFilter,
    private val participantsOrderByName: ParticipantsOrderByName
) : CallingParticipantsOrder {

    override fun reorderItems(participants: List<Participant>): List<Participant> {
        return if (participants.isNotEmpty()) {
            val selfUserId = userRepository.getSelfUserId()

            val participantsWithoutUserId = participantsFilter.participantsWithoutUserId(participants, selfUserId)
            val participantsWithUserIdOnly = participantsFilter.selfParticipants(participants, selfUserId)
            val participantsSharingScreen = participantsFilter.participantsWithScreenSharingOn(participants)
            val participantsWithVideoOn = participantsFilter.participantsByCamera(
                participants = participantsWithoutUserId,
                isCameraOn = true
            )
            val participantsWithVideoOff = participantsFilter.participantsByCamera(
                participants = participantsWithoutUserId,
                isCameraOn = false
            )

            val participantsSharingScreenSortedByName = participantsOrderByName.sortItems(participantsSharingScreen)
            val participantsWithVideoOnSortedByName = participantsOrderByName.sortItems(participantsWithVideoOn)
            val participantsWithVideoOffSortedByName = participantsOrderByName.sortItems(participantsWithVideoOff)

            val sortedParticipantsByName =
                participantsSharingScreenSortedByName + participantsWithVideoOnSortedByName + participantsWithVideoOffSortedByName
            return participantsWithUserIdOnly + sortedParticipantsByName
        } else participants
    }
}
