/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.fold

internal interface CallingParticipantsOrder {
    suspend fun reorderItems(
        participants: List<Participant>,
        orderType: CallingParticipantsOrderType = CallingParticipantsOrderType.ALL_MEDIA_FIRST
    ): List<Participant>
}

internal class CallingParticipantsOrderImpl(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val participantsFilter: ParticipantsFilter,
    private val participantsOrderByName: ParticipantsOrderByName,
    private val selfUserId: UserId
) : CallingParticipantsOrder {

    override suspend fun reorderItems(
        participants: List<Participant>,
        orderType: CallingParticipantsOrderType
    ): List<Participant> = if (participants.isNotEmpty()) {
        val (selfParticipant, otherParticipants) = currentClientIdProvider().fold(
            {
                null to participants // cannot determine, so return null as self participant and all list as other participants
            },
            { selfClientId ->
                val selfParticipant = participantsFilter.selfParticipant(participants, selfUserId, selfClientId.value)
                val otherParticipants = participantsFilter.otherParticipants(participants, selfClientId.value)
                selfParticipant to otherParticipants
            }
        )

        when (orderType) {
            CallingParticipantsOrderType.ALL_MEDIA_FIRST -> {
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

                listOfNotNull(selfParticipant) + sortedParticipantsByName
            }

            CallingParticipantsOrderType.PRESENTERS_FIRST -> {
                val participantsSharingScreen = participantsFilter.participantsSharingScreen(otherParticipants, true)
                val participantsNotSharingScreen = participantsFilter.participantsSharingScreen(otherParticipants, false)

                val participantsSharingScreenSortedByName = participantsOrderByName.sortItems(participantsSharingScreen)
                val participantsNotSharingScreenSortedByName = participantsOrderByName.sortItems(participantsNotSharingScreen)

                val sortedParticipantsByName = participantsSharingScreenSortedByName + participantsNotSharingScreenSortedByName

                listOfNotNull(selfParticipant) + sortedParticipantsByName
            }

            CallingParticipantsOrderType.ALPHABETICALLY -> {
                val sortedParticipants = participantsOrderByName.sortItems(otherParticipants)
                listOfNotNull(selfParticipant) + sortedParticipants
            }
        }
    } else participants
}

public enum class CallingParticipantsOrderType {
    /**
     * Participants are sorted in the following order:
     * - self participant, always on top, no matter if sharing screen, has video on or off
     * - participants sharing screen alphabetically
     * - participants with video on alphabetically
     * - participants with video off alphabetically
     */
    ALL_MEDIA_FIRST,

    /**
     * Participants are sorted in the following order:
     * - self participant, always on top, no matter if sharing screen, has video on or off
     * - participants sharing screen alphabetically
     * - all other participants alphabetically
     */
    PRESENTERS_FIRST,

    /**
     * Participants are sorted in the following order:
     * - self participant, always on top, no matter if sharing screen, has video on or off
     * - all other participants alphabetically
     */
    ALPHABETICALLY
}
