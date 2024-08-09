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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.authenticated.conversation.SubconversationResponse
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MLSCallHelperTest {

    @Test
    fun givenMlsProtocol_whenShouldEndSFTOneOnOneCallIsCalled_thenReturnCorrectValue() =
        runTest {
            val (_, mLSCallHelper) = Arrangement()
                .withShouldUseSFTForOneOnOneCallsReturning(Either.Right(true))
                .arrange()

            // one participant in the call
            val shouldEndSFTOneOnOneCall1 = mLSCallHelper.shouldEndSFTOneOnOneCall(
                conversationId = conversationId,
                callProtocol = CONVERSATION_MLS_PROTOCOL_INFO,
                conversationType = Conversation.Type.ONE_ON_ONE,
                newCallParticipants = listOf(participant1),
                previousCallParticipants = listOf(participant1)
            )
            assertFalse { shouldEndSFTOneOnOneCall1 }

            // Audio not lost for the second participant
            val shouldEndSFTOneOnOneCall2 = mLSCallHelper.shouldEndSFTOneOnOneCall(
                conversationId = conversationId,
                callProtocol = CONVERSATION_MLS_PROTOCOL_INFO,
                conversationType = Conversation.Type.GROUP,
                newCallParticipants = listOf(participant1, participant2),
                previousCallParticipants = listOf(participant1, participant2)
            )
            assertFalse { shouldEndSFTOneOnOneCall2 }

            // Audio lost for the second participant
            val shouldEndSFTOneOnOneCall3 = mLSCallHelper.shouldEndSFTOneOnOneCall(
                conversationId = conversationId,
                callProtocol = CONVERSATION_MLS_PROTOCOL_INFO,
                conversationType = Conversation.Type.ONE_ON_ONE,
                previousCallParticipants = listOf(participant1, participant2),
                newCallParticipants = listOf(
                    participant1,
                    participant2.copy(hasEstablishedAudio = false)
                )
            )
            assertTrue { shouldEndSFTOneOnOneCall3 }
        }

    @Test
    fun givenProteusProtocol_whenShouldEndSFTOneOnOneCallIsCalled_thenReturnCorrectValue() =
        runTest {

            val (_, mLSCallHelper) = Arrangement()
                .withShouldUseSFTForOneOnOneCallsReturning(Either.Right(true))
                .arrange()

            // participants list has 2 items for the new list and the previous list
            val shouldEndSFTOneOnOneCall1 = mLSCallHelper.shouldEndSFTOneOnOneCall(
                conversationId = conversationId,
                callProtocol = Conversation.ProtocolInfo.Proteus,
                conversationType = Conversation.Type.ONE_ON_ONE,
                newCallParticipants = listOf(participant1, participant2),
                previousCallParticipants = listOf(participant1, participant2)
            )
            assertFalse { shouldEndSFTOneOnOneCall1 }

            // new participants list has 1 participant
            val shouldEndSFTOneOnOneCall2 = mLSCallHelper.shouldEndSFTOneOnOneCall(
                conversationId = conversationId,
                callProtocol = Conversation.ProtocolInfo.Proteus,
                conversationType = Conversation.Type.ONE_ON_ONE,
                newCallParticipants = listOf(participant1),
                previousCallParticipants = listOf(participant1, participant2)
            )
            assertTrue { shouldEndSFTOneOnOneCall2 }
        }

    @Test
    fun givenMLSOneOnOneCallAndShouldUseSFTForOneOnOneCall_whenHandleCallTerminationIsCalled_thenDeleteRemoteSubConversation() =
        runTest {
            val (arrangement, mLSCallHelper) = Arrangement()
                .withShouldUseSFTForOneOnOneCallsReturning(Either.Right(true))
                .withFetchRemoteSubConversationDetailsReturning(
                    Either.Right(subconversationResponse)
                )
                .withDeleteRemoteSubConversationSuccess()
                .arrange()

            mLSCallHelper.handleCallTermination(conversationId, Conversation.Type.ONE_ON_ONE)

            coVerify {
                arrangement.subconversationRepository.deleteRemoteSubConversation(any(), any(), any())
            }.wasInvoked(exactly = once)
        }

    @Test
    fun givenSubconversationRepositoryReturnFailure_whenHandleCallTerminationIsCalled_thenDoNotDeleteRemoteSubConversation() =
        runTest {
            val (arrangement, mLSCallHelper) = Arrangement()
                .withShouldUseSFTForOneOnOneCallsReturning(Either.Right(true))
                .withFetchRemoteSubConversationDetailsReturning(
                    Either.Left(
                        NetworkFailure.ServerMiscommunication(
                            TestNetworkException.badRequest
                        )
                    )
                )
                .arrange()

            mLSCallHelper.handleCallTermination(conversationId, Conversation.Type.ONE_ON_ONE)

            coVerify {
                arrangement.subconversationRepository.deleteRemoteSubConversation(any(), any(), any())
            }.wasNotInvoked()
        }

    @Test
    fun givenShouldNotUseSFTForOneOnOneCall_whenHandleCallTerminationIsCalled_thenLeaveMlsConference() =
        runTest {
            val (arrangement, mLSCallHelper) = Arrangement()
                .withShouldUseSFTForOneOnOneCallsReturning(Either.Right(false))
                .arrange()

            mLSCallHelper.handleCallTermination(conversationId, Conversation.Type.GROUP)

            coVerify {
                arrangement.callRepository.leaveMlsConference(any())
            }.wasInvoked(exactly = once)
        }

    @Test
    fun givenMLSGroupCall_whenHandleCallTerminationIsCalled_thenLeaveMlsConference() =
        runTest {
            val (arrangement, mLSCallHelper) = Arrangement()
                .withShouldUseSFTForOneOnOneCallsReturning(Either.Right(true))
                .arrange()

            mLSCallHelper.handleCallTermination(conversationId, Conversation.Type.GROUP)

            coVerify {
                arrangement.callRepository.leaveMlsConference(eq(conversationId))
            }.wasInvoked(exactly = once)
        }

    private class Arrangement {

        @Mock
        val callRepository = mock(classOf<CallRepository>())

        @Mock
        val subconversationRepository = mock(classOf<SubconversationRepository>())

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        private val mLSCallHelper: MLSCallHelper = MLSCallHelperImpl(
            callRepository = callRepository,
            subconversationRepository = subconversationRepository,
            userConfigRepository = userConfigRepository
        )

        fun arrange() = this to mLSCallHelper

        fun withShouldUseSFTForOneOnOneCallsReturning(result: Either<StorageFailure, Boolean>) =
            apply {
                every { userConfigRepository.shouldUseSFTForOneOnOneCalls() }.returns(result)
            }

        suspend fun withFetchRemoteSubConversationDetailsReturning(result: Either<NetworkFailure, SubconversationResponse>) =
            apply {
                coEvery {
                    subconversationRepository.fetchRemoteSubConversationDetails(eq(conversationId), eq(CALL_SUBCONVERSATION_ID))
                }.returns(result)
            }

        suspend fun withDeleteRemoteSubConversationSuccess() =
            apply {
                coEvery {
                    subconversationRepository.deleteRemoteSubConversation(any(), any(), any())
                }.returns(Either.Right(Unit))
            }
    }

    companion object {
        val conversationId = ConversationId(value = "convId", domain = "domainId")
        val CONVERSATION_MLS_PROTOCOL_INFO = Conversation.ProtocolInfo.MLS(
            GroupID("GROUP_ID"),
            Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
            5UL,
            Instant.parse("2021-03-30T15:36:00.000Z"),
            cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        )
        val participant1 = Participant(
            id = QualifiedID("participantId", "participantDomain"),
            clientId = "abcd",
            name = "name",
            isMuted = true,
            isSpeaking = false,
            isCameraOn = false,
            avatarAssetId = null,
            isSharingScreen = false,
            hasEstablishedAudio = true
        )
        val participant2 = participant1.copy(
            id = QualifiedID("participantId2", "participantDomain2"),
            clientId = "efgh"
        )
        val subconversationResponse = SubconversationResponse(
            id = "subconversationId",
            parentId = com.wire.kalium.network.api.model.ConversationId(
                "conversationId",
                "domainId"
            ),
            groupId = "groupId",
            epoch = 1UL,
            epochTimestamp = "2021-03-30T15:36:00.000Z",
            mlsCipherSuiteTag = 5,
            members = listOf()
        )
    }
}
