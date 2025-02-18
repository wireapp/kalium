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
    suspend fun reorderItems(participants: List<Participant>): List<Participant>
}

internal class CallingParticipantsOrderImpl(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val participantsFilter: ParticipantsFilter,
    private val participantsOrderByName: ParticipantsOrderByName,
    private val selfUserId: UserId
) : CallingParticipantsOrder {

    override suspend fun reorderItems(participants: List<Participant>): List<Participant> {
        return if (participants.isNotEmpty()) {
            currentClientIdProvider().fold({
                participants
            }, { selfClientId ->

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
