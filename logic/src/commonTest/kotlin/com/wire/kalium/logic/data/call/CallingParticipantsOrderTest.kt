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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.functional.Either
import io.mockative.any
import io.mockative.eq
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.times
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CallingParticipantsOrderTest {

    private val userRepository = mock(UserRepository::class)
    private val participantsFilter = mock(ParticipantsFilter::class)
    private val currentClientIdProvider = mock(CurrentClientIdProvider::class)
    private val participantsOrderByName = mock(ParticipantsOrderByName::class)

    private lateinit var callingParticipantsOrder: CallingParticipantsOrder

    @BeforeTest
    fun setup() {
        callingParticipantsOrder =
            CallingParticipantsOrderImpl(userRepository, currentClientIdProvider, participantsFilter, participantsOrderByName)
    }

    @Test
    fun givenAnEmptyListOfParticipants_whenOrderingParticipants_thenDoNotOrderItemsAndReturnEmptyList() = runTest {
        val emptyParticipantsList = listOf<Participant>()

        val result = callingParticipantsOrder.reorderItems(emptyParticipantsList)

        assertEquals(emptyParticipantsList, result)
    }

    @Test
    fun givenANullClientIdWhenOrderingParticipants_thenReturnDoNotOrder() = runTest {
        val participants = listOf(participant3, participant4)
        coEvery {
            currentClientIdProvider.invoke()
        }.returns(Either.Left(CoreFailure.MissingClientRegistration))

        val result = callingParticipantsOrder.reorderItems(participants)

        assertEquals(participants, result)
    }

    @Test
    fun givenAListOfParticipants_whenOrderingParticipants_thenOrderItemsAlphabeticallyByNameExceptFirstOne() = runTest {
        coEvery {
            currentClientIdProvider.invoke()
        }.returns(Either.Right(ClientId(selfClientId)))

        coEvery {
            userRepository.getSelfUser()
        }.returns(selfUser)

        every {
            participantsFilter.otherParticipants(participants, selfClientId)
        }.returns(otherParticipants)

        every {
            participantsFilter.selfParticipant(participants, selfUserId, selfClientId)
        }.returns(participant1)

        every {
            participantsFilter.participantsSharingScreen(otherParticipants, true)
        }.returns(participantsSharingScreen)

        every {
            participantsFilter.participantsSharingScreen(otherParticipants, false)
        }.returns(participantsNotSharingScreen)

        every {
            participantsFilter.participantsByCamera(participantsNotSharingScreen, true)
        }.returns(participantsWithCameraOn)

        every {
            participantsFilter.participantsByCamera(participantsNotSharingScreen, false)
        }.returns(participantsWithCameraOff)

        every {
            participantsOrderByName.sortItems(participantsWithCameraOff)
        }.returns(listOf(participant3, participant11))

        every {
            participantsOrderByName.sortItems(participantsWithCameraOn)
        }.returns(listOf(participant2))

        every {
            participantsOrderByName.sortItems(participantsSharingScreen)
        }.returns(listOf(participant4))

        val result = callingParticipantsOrder.reorderItems(participants)

        assertEquals(participants.size, result.size)
        assertEquals(participant1, result.first())
        assertEquals(participant11, result.last())

        coVerify {
            currentClientIdProvider()
        }.wasInvoked(exactly = once)

        verify {
            participantsFilter.otherParticipants(eq(participants), eq(selfClientId))
        }.wasInvoked(exactly = once)

        verify {
            participantsFilter.selfParticipant(eq(participants), eq(selfUserId), eq(selfClientId))
        }.wasInvoked(exactly = once)

        verify {
            participantsFilter.participantsSharingScreen(eq(otherParticipants), eq(true))
        }.wasInvoked(exactly = once)

        verify {
            participantsFilter.participantsByCamera(any(), any())
        }.wasInvoked(exactly = twice)

        verify {
            participantsOrderByName.sortItems(any())
        }.wasInvoked(exactly = 3.times)
    }

    companion object {
        private val selfUserId = QualifiedID("participant1", "domain")
        private val selfUser = SelfUser(
            id = selfUserId,
            name = null,
            handle = null,
            email = null,
            phone = null,
            accentId = 0,
            teamId = null,
            connectionStatus = ConnectionState.NOT_CONNECTED,
            previewPicture = null,
            completePicture = null,
            availabilityStatus = UserAvailabilityStatus.AVAILABLE,
            expiresAt = null,
            supportedProtocols = null,
            userType = UserType.INTERNAL,
        )

        const val selfClientId = "client1"
        val participant1 = Participant(
            id = selfUserId,
            clientId = selfClientId,
            isMuted = false,
            isCameraOn = false,
            name = "self user",
            hasEstablishedAudio = true,
            accentId = 0
        )
        val participant2 = Participant(
            id = QualifiedID("participant2", "domain"),
            clientId = "client2",
            isMuted = false,
            isCameraOn = true,
            name = "user name",
            hasEstablishedAudio = true,
            accentId = 0
        )
        val participant3 = Participant(
            id = QualifiedID("participant3", "domain"),
            clientId = "client3",
            isMuted = false,
            isCameraOn = false,
            name = "A random name",
            hasEstablishedAudio = true,
            accentId = 0
        )
        val participant4 = Participant(
            id = QualifiedID("participant4", "domain"),
            clientId = "client4",
            isMuted = false,
            isCameraOn = false,
            isSharingScreen = true,
            name = "A random name",
            hasEstablishedAudio = true,
            accentId = 0
        )
        val participant11 = Participant(
            id = selfUserId,
            clientId = "client11",
            isMuted = false,
            isCameraOn = false,
            name = "self user",
            hasEstablishedAudio = true,
            accentId = 0
        )
        val participants = listOf(participant1, participant2, participant3, participant4, participant11)
        val otherParticipants = listOf(participant2, participant3, participant4, participant11)
        val participantsSharingScreen = listOf(participant4)
        val participantsNotSharingScreen = listOf(participant2, participant3, participant11)
        val participantsWithCameraOn = listOf(participant2)
        val participantsWithCameraOff = listOf(participant3, participant11)

    }
}
