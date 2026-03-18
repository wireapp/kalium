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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedID
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CallingParticipantsOrderTest {

    @Test
    fun givenAnEmptyListOfParticipants_whenOrderingParticipants_thenDoNotOrderItemsAndReturnEmptyList() = runTest {
        // given
        val emptyParticipantsList = listOf<Participant>()
        val (_, callingParticipantsOrder) = Arrangement().arrange()
        // when
        val result = callingParticipantsOrder.reorderItems(emptyParticipantsList)
        // then
        assertEquals(emptyParticipantsList, result)
    }

    @Test
    fun givenAListOfParticipants_andNotKnownCurrentClient_whenOrderingByVideosFirst_thenOrderAllItemsProperly() = runTest {
        // given
        val (arrangement, callingParticipantsOrder) = Arrangement()
            .withCurrentClientIdProviderReturning(Either.Left(CoreFailure.MissingClientRegistration))
            .arrange()
        val expected = with(arrangement) {
            // no self participant at the beginning as it cannot be determined
            allParticipants.sharingScreen().sortedByName() + // first all participants, including self, sharing screen sorted by name
                    allParticipants.notSharingScreen().withCameraOn().sortedByName() + // then others with camera on sorted by name
                    allParticipants.notSharingScreen().withCameraOff().sortedByName() // then the rest sorted by name
        }
        // when
        val result = callingParticipantsOrder.reorderItems(arrangement.allParticipants, CallingParticipantsOrderType.VIDEOS_FIRST)
        // then
        assertEquals(expected, result)
    }

    @Test
    fun givenAListOfParticipants_andKnownCurrentClientWithoutVideo_whenOrderingByVideosFirst_thenOrderItemsProperly() = runTest {
        // given
        val (arrangement, callingParticipantsOrder) = Arrangement()
            .withCurrentClientIdProviderReturning(Either.Right(ClientId(selfClientId)))
            .arrange()
        val expected = with(arrangement) {
            listOf(selfParticipant) + // self participant first, even if not sharing screen and with camera off
                    otherParticipants.sharingScreen().sortedByName() + // then other participants sharing screen sorted by name
                    otherParticipants.notSharingScreen().withCameraOn().sortedByName() + // then others with camera on sorted by name
                    otherParticipants.notSharingScreen().withCameraOff().sortedByName() // then the rest sorted by name
        }
        // when
        val result = callingParticipantsOrder.reorderItems(arrangement.allParticipants, CallingParticipantsOrderType.VIDEOS_FIRST)
        // then
        assertEquals(expected, result)
    }

    @Test
    fun givenAListOfParticipants_andKnownCurrentClientWithVideo_whenOrderingByVideosFirst_thenOrderItemsProperly() = runTest {
        // given
        val (arrangement, callingParticipantsOrder) = Arrangement()
            .withCurrentClientIdProviderReturning(Either.Right(ClientId(selfClientId)))
            .arrange()
        val expected = with(arrangement) {
            listOf(selfParticipant) + // self participant first
                    otherParticipants.sharingScreen().sortedByName() + // then other participants sharing screen sorted by name
                    otherParticipants.notSharingScreen().withCameraOn().sortedByName() + // then others with camera on sorted by name
                    otherParticipants.notSharingScreen().withCameraOff().sortedByName() // then the rest sorted by name
        }
        // when
        val result = callingParticipantsOrder.reorderItems(arrangement.allParticipants, CallingParticipantsOrderType.VIDEOS_FIRST)
        // then
        assertEquals(expected, result)
    }

    @Test
    fun givenAListOfParticipants_andNotKnownCurrentClient_whenOrderingAlphabetically_thenOrderAllItemsProperly() = runTest {
        // given
        val (arrangement, callingParticipantsOrder) = Arrangement()
            .withCurrentClientIdProviderReturning(Either.Left(CoreFailure.MissingClientRegistration))
            .arrange()
        val expected = with(arrangement) {
            // no self participant at the beginning as it cannot be determined
            allParticipants.sortedByName() // all participants, including self, sorted by name regardless of video or screen sharing
        }
        // when
        val result = callingParticipantsOrder.reorderItems(arrangement.allParticipants, CallingParticipantsOrderType.ALPHABETICALLY)
        // then
        assertEquals(expected, result)
    }

    @Test
    fun givenAListOfParticipants_andKnownCurrentClientWithoutVideo_whenOrderingAlphabetically_thenOrderItemsProperly() = runTest {
        // given
        val (arrangement, callingParticipantsOrder) = Arrangement()
            .withCurrentClientIdProviderReturning(Either.Right(ClientId(selfClientId)))
            .arrange()
        val expected = listOf(arrangement.selfParticipant) + // self participant first, even if not first alphabetically
                arrangement.otherParticipants.sortedByName() // then others sorted by name regardless of video or screen sharing
        // when
        val result = callingParticipantsOrder.reorderItems(arrangement.allParticipants, CallingParticipantsOrderType.ALPHABETICALLY)
        // then
        assertEquals(expected, result)
    }

    @Test
    fun givenAListOfParticipants_andKnownCurrentClientWithVideo_whenOrderingAlphabetically_thenOrderItemsProperly() = runTest {
        // given
        val (arrangement, callingParticipantsOrder) = Arrangement()
            .withCurrentClientIdProviderReturning(Either.Right(ClientId(selfClientId)))
            .arrange()
        val expected = listOf(arrangement.selfParticipant) + // self participant first, even if not first alphabetically
                arrangement.otherParticipants.sortedByName() // then others sorted by name regardless of video or screen sharing
        // when
        val result = callingParticipantsOrder.reorderItems(arrangement.allParticipants, CallingParticipantsOrderType.ALPHABETICALLY)
        // then
        assertEquals(expected, result)
    }

    private inner class Arrangement() {

        val participantsFilter = mock<ParticipantsFilter>()
        val currentClientIdProvider = mock<CurrentClientIdProvider>()
        val participantsOrderByName = mock<ParticipantsOrderByName>()

        var selfParticipant = participant1
            private set
        var otherParticipants: List<Participant> = listOf(participant5, participant2, participant4, participant3) // unordered list
            private set
        val allParticipants get() = listOf(selfParticipant) + otherParticipants

        fun withCurrentClientIdProviderReturning(result: Either<CoreFailure, ClientId>) = apply {
            everySuspend { currentClientIdProvider.invoke() } returns result
        }

        init {
            every { participantsFilter.otherParticipants(any(), selfClientId)
            } returns otherParticipants
            every {
                participantsFilter.selfParticipant(any(), selfUserId, selfClientId)
            } returns selfParticipant
            every {
                participantsFilter.participantsSharingScreen(any(), any())
            } calls { (participants: List<Participant>, isSharingScreen: Boolean) ->
                participants.filter { it.isSharingScreen == isSharingScreen }
            }
            every {
                participantsFilter.participantsByCamera(any(), any())
            } calls { (participants: List<Participant>, isCameraOn: Boolean) ->
                participants.filter { it.isCameraOn == isCameraOn }
            }
            every {
                participantsOrderByName.sortItems(any())
            } calls { (participants: List<Participant>) ->
                participants.sortedByName()
            }
        }

        fun arrange() = this to CallingParticipantsOrderImpl(
            currentClientIdProvider = currentClientIdProvider,
            participantsFilter = participantsFilter,
            selfUserId = selfUserId,
            participantsOrderByName = participantsOrderByName
        )
    }

    private fun List<Participant>.sortedByName() = sortedBy { it.name?.uppercase() }
    private fun List<Participant>.sharingScreen() = filter { it.isSharingScreen }
    private fun List<Participant>.notSharingScreen() = filter { !it.isSharingScreen }
    private fun List<Participant>.withCameraOn() = filter { it.isCameraOn }
    private fun List<Participant>.withCameraOff() = filter { !it.isCameraOn }

    companion object {
        private val selfUserId = QualifiedID("participant1", "domain")
        private const val selfClientId = "client1"
        private val participant1 = Participant(
            id = selfUserId,
            clientId = selfClientId,
            isMuted = false,
            isCameraOn = false,
            name = "Self user 1",
            hasEstablishedAudio = true,
            accentId = 0
        )
        private  val participant2 = Participant(
            id = QualifiedID("participant2", "domain"),
            clientId = "client2",
            isMuted = false,
            isCameraOn = true,
            name = "Other user 2",
            hasEstablishedAudio = true,
            accentId = 0
        )
        private val participant3 = Participant(
            id = QualifiedID("participant3", "domain"),
            clientId = "client3",
            isMuted = false,
            isCameraOn = false,
            name = "Other user 3",
            hasEstablishedAudio = true,
            accentId = 0
        )
        private val participant4 = Participant(
            id = QualifiedID("participant4", "domain"),
            clientId = "client4",
            isMuted = false,
            isCameraOn = false,
            isSharingScreen = true,
            name = "Other user 4",
            hasEstablishedAudio = true,
            accentId = 0
        )
        private val participant5 = Participant(
            id = selfUserId,
            clientId = "client5",
            isMuted = false,
            isCameraOn = true,
            name = "Other user 5",
            hasEstablishedAudio = true,
            accentId = 0
        )
    }
}
