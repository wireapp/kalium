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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import io.mockative.coEvery
import io.mockative.mock
import io.mockative.of
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CallHelperTest {

    private fun testShouldEndSFT1on1Call(
        shouldUseSFTForOneOnOneCalls: Either<StorageFailure, Boolean> = true.right(),
        establishedCall: Call? = call,
        newCallParticipants: List<ParticipantMinimized> = listOf(participantMinimized1),
        expected: Boolean
    ) = runTest {
        val (_, callHelper) = Arrangement()
            .withShouldUseSFTForOneOnOneCallsReturning(shouldUseSFTForOneOnOneCalls)
            .withEstablishedCallsFlowReturning(listOfNotNull(establishedCall))
            .arrange()
        assertEquals(expected, callHelper.shouldEndSFTOneOnOneCall(conversationId, newCallParticipants))
    }

    @Test
    fun givenSFTFor1on1CallsConfigNotFound_whenShouldEndSFTOneOnOneCallIsCalled_thenReturnFalse() =
        testShouldEndSFT1on1Call(shouldUseSFTForOneOnOneCalls = StorageFailure.DataNotFound.left(), expected = false)

    @Test
    fun givenSFTShouldNotBeUsedFor1on1Calls_whenShouldEndSFTOneOnOneCallIsCalled_thenReturnFalse() =
        testShouldEndSFT1on1Call(shouldUseSFTForOneOnOneCalls = false.right(), expected = false)

    @Test
    fun givenNotEstablishedCall_whenShouldEndSFTOneOnOneCallIsCalled_thenReturnFalse() =
        testShouldEndSFT1on1Call(establishedCall = null, expected = false)

    @Test
    fun givenEstablishedNon1on1Call_whenShouldEndSFTOneOnOneCallIsCalled_thenReturnFalse() =
        testShouldEndSFT1on1Call(establishedCall = call.copy(conversationType = Conversation.Type.Group.Regular), expected = false)

    @Test
    fun givenEstablished1on1CallWith1Participant_andParticipantsDidNotChange_whenShouldEndSFTOneOnOneCallIsCalled_thenReturnFalse() =
        testShouldEndSFT1on1Call(
            establishedCall = call.copy(participants = listOf(participant1)),
            newCallParticipants = listOf(participantMinimized1),
            expected = false
        )

    @Test
    fun givenEstablished1on1CallWith2Participants_andParticipantsDidNotChange_whenShouldEndSFTOneOnOneCallIsCalled_thenReturnFalse() =
        testShouldEndSFT1on1Call(
            establishedCall = call.copy(participants = listOf(participant1, participant2)),
            newCallParticipants = listOf(participantMinimized1, participantMinimized2),
            expected = false
        )

    @Test
    fun givenEstablished1on1CallWith1Participant_andOneParticipantJoined_whenShouldEndSFTOneOnOneCallIsCalled_thenReturnFalse() =
        testShouldEndSFT1on1Call(
            establishedCall = call.copy(participants = listOf(participant1)),
            newCallParticipants = listOf(participantMinimized1, participantMinimized2),
            expected = false
        )

    @Test
    fun givenEstablished1on1CallWith2Participants_andOneParticipantLeft_whenShouldEndSFTOneOnOneCallIsCalled_thenReturnTrue() =
        testShouldEndSFT1on1Call(
            establishedCall = call.copy(participants = listOf(participant1, participant2)),
            newCallParticipants = listOf(participantMinimized1),
            expected = true
        )

    private class Arrangement {

        val userConfigRepository = mock(of<UserConfigRepository>())
        val callRepository = mock(of<CallRepository>())
        private val mLSCallHelper: CallHelper = CallHelperImpl(userConfigRepository, callRepository)

        fun arrange() = this to mLSCallHelper

        suspend fun withShouldUseSFTForOneOnOneCallsReturning(result: Either<StorageFailure, Boolean>) = apply {
            coEvery {
                userConfigRepository.shouldUseSFTForOneOnOneCalls()
            }.returns(result)
        }

        suspend fun withEstablishedCallsFlowReturning(calls: List<Call>) = apply {
            coEvery {
                callRepository.establishedCallsFlow()
            }.returns(flowOf(calls))
        }
    }

    companion object {
        val conversationId = ConversationId(value = "convId", domain = "domainId")
        val participant1 = Participant(
            id = QualifiedID("participantId", "participantDomain"),
            clientId = "abcd",
            name = "name",
            isMuted = true,
            isSpeaking = false,
            isCameraOn = false,
            avatarAssetId = null,
            isSharingScreen = false,
            hasEstablishedAudio = true,
            accentId = 0
        )
        val participant2 = participant1.copy(
            id = QualifiedID("participantId2", "participantDomain2"),
            clientId = "efgh"
        )
        val participantMinimized1 = ParticipantMinimized(
            id = QualifiedID("participantId", "participantDomain"),
            userId = QualifiedID("participantId", "participantDomain"),
            clientId = "abcd",
            isMuted = true,
            isCameraOn = false,
            isSharingScreen = false,
            hasEstablishedAudio = true
        )
        val participantMinimized2 = participantMinimized1.copy(
            id = QualifiedID("participantId2", "participantDomain2"),
            clientId = "efgh"
        )
        val call = Call(
            conversationId = conversationId,
            status = CallStatus.ESTABLISHED,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            callerId = UserId("callerId", "domain"),
            conversationName = "Conversation Name",
            conversationType = Conversation.Type.OneOnOne,
            callerName = "name",
            callerTeamName = "team",
            participants = listOf(participant1, participant2)
        )
    }
}
