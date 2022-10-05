package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.functional.fold

internal interface CallingParticipantsOrder {
    suspend fun reorderItems(participants: List<Participant>): List<Participant>
}

internal class CallingParticipantsOrderImpl(
    private val userRepository: UserRepository,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val participantsFilter: ParticipantsFilter,
    private val participantsOrderByName: ParticipantsOrderByName,
) : CallingParticipantsOrder {

    override suspend fun reorderItems(participants: List<Participant>): List<Participant> {
        return if (participants.isNotEmpty()) {
            currentClientIdProvider().fold({
                participants
            }, { selfClientId ->
                val selfUserId = userRepository.getSelfUser()?.id!!

                val selfParticipant = participantsFilter.selfParticipant(participants, selfUserId, selfClientId.value)
                val otherParticipants = participantsFilter.otherParticipants(participants, selfClientId.value)

                val participantsSharingScreen = participantsFilter.participantsSharingScreen(otherParticipants, true)
                val participantsNotSharingScreen = participantsFilter.participantsSharingScreen(otherParticipants, false)

                val participantsWithVideoOn = participantsFilter.participantsByCamera(
                    participants = participantsNotSharingScreen,
                    isCameraOn = true
                )
                val participantsWithVideoOff = participantsFilter.participantsByCamera(
                    participants = participantsNotSharingScreen,
                    isCameraOn = false
                )
                val participantsSharingScreenSortedByName = participantsOrderByName.sortItems(participantsSharingScreen)
                val participantsWithVideoOnSortedByName = participantsOrderByName.sortItems(participantsWithVideoOn)
                val participantsWithVideoOffSortedByName = participantsOrderByName.sortItems(participantsWithVideoOff)

                val sortedParticipantsByName =
                    participantsSharingScreenSortedByName + participantsWithVideoOnSortedByName + participantsWithVideoOffSortedByName

                return mutableListOf(selfParticipant) + sortedParticipantsByName
            })
        } else participants
    }
}
