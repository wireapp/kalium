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

import app.cash.turbine.test
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.call.CallRepositoryTest.Arrangement.Companion.callerId
import com.wire.kalium.logic.data.call.CallRepositoryTest.Arrangement.Companion.participant
import com.wire.kalium.logic.data.call.mapper.CallMapperImpl
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.EpochChangesObserver
import com.wire.kalium.logic.data.conversation.JoinSubconversationUseCase
import com.wire.kalium.logic.data.conversation.LeaveSubconversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.FederatedIdMapperImpl
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedClientID
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.CallApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.call.CallDAO
import com.wire.kalium.persistence.dao.call.CallEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import io.ktor.util.reflect.instanceOf
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
class CallRepositoryTest {

    @Test
    fun whenRequestingCallConfig_withNoLimitParam_ThenAResultIsReturned() = runTest {
        val (_, callRepository) = Arrangement()
            .givenGetCallConfigResponse(NetworkResponse.Success(Arrangement.CALL_CONFIG_API_RESPONSE, mapOf(), 200))
            .arrange()

        val result = callRepository.getCallConfigResponse(limit = null)

        result.shouldSucceed {
            assertEquals(Arrangement.CALL_CONFIG_API_RESPONSE, it)
        }
    }

    @Test
    fun givenEmptyListOfCalls_whenGetAllCallsIsCalled_thenReturnAnEmptyListOfCalls() = runTest {
        val (_, callRepository) = Arrangement()
            .givenObserveCallsReturns(flowOf(listOf()))
            .arrange()

        val calls = callRepository.callsFlow()

        calls.test {
            assertEquals(0, awaitItem().size)
        }
    }

    @Test
    fun givenAListOfCallProfiles_whenGetAllCallsIsCalled_thenReturnAListOfCalls() = runTest {
        val (_, callRepository) = Arrangement()
            .givenObserveCallsReturns(
                flowOf(
                    listOf(
                        createCallEntity().copy(
                            status = CallEntity.Status.ESTABLISHED,
                            conversationType = ConversationEntity.Type.ONE_ON_ONE,
                            callerId = "callerId@domain"
                        )
                    )
                )
            )
            .arrange()

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    Arrangement.conversationId to createCallMetadata().copy(
                        isMuted = false,
                        conversationName = "ONE_ON_ONE Name",
                        conversationType = Conversation.Type.ONE_ON_ONE,
                        callerName = "otherUsername",
                        callerTeamName = "team_1"
                    )
                )
            )
        )

        val calls = callRepository.callsFlow()

        val expectedCall = provideCall(
            id = Arrangement.conversationId,
            status = CallStatus.ESTABLISHED
        )

        calls.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(expectedCall, list[0])
            assertTrue(list[0].instanceOf(Call::class))
        }
    }

    @Test
    fun whenStartingAGroupCall_withNoExistingCall_ThenSaveCallToDatabase() = runTest {
        // given
        val (arrangement, callRepository) = Arrangement()
            .givenObserveConversationDetailsByIdReturns(
                flowOf(
                    Either.Right(
                        ConversationDetails.Group(
                            Arrangement.groupConversation,
                            false,
                            isSelfUserMember = true,
                            selfRole = Conversation.Member.Role.Member
                        )
                    )
                )
            )
            .givenGetKnownUserSucceeds()
            .givenGetTeamSucceeds()
            .givenGetCallStatusByConversationIdReturns(null)
            .givenInsertCallSucceeds()
            .arrange()

        // when
        callRepository.createCall(
            conversationId = Arrangement.conversationId,
            status = CallStatus.STARTED,
            callerId = callerId,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationTypeForCall.Conference
        )

        // then
        coVerify {
            arrangement.callDAO.insertCall(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun whenStartingAGroupCall_withExistingClosedCall_ThenSaveCallToDatabase() = runTest {
        val (arrangement, callRepository) = Arrangement()
            .givenObserveConversationDetailsByIdReturns(
                flowOf(
                    Either.Right(
                        ConversationDetails.Group(
                            Arrangement.groupConversation,
                            isSelfUserMember = true,
                            selfRole = Conversation.Member.Role.Member
                        )
                    )
                )
            )
            .givenGetKnownUserSucceeds()
            .givenGetTeamSucceeds()
            .givenGetCallStatusByConversationIdReturns(CallEntity.Status.CLOSED)
            .givenInsertCallSucceeds()
            .arrange()

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    Arrangement.conversationId to createCallMetadata().copy(
                        isMuted = false
                    )
                )
            )
        )

        // when
        callRepository.createCall(
            conversationId = Arrangement.conversationId,
            status = CallStatus.STARTED,
            callerId = callerId,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationTypeForCall.Conference
        )

        // then
        coVerify {
            arrangement.callDAO.insertCall(any())
        }.wasInvoked(exactly = once)

        assertEquals(
            true,
            callRepository.getCallMetadataProfile().data[Arrangement.conversationId]?.isMuted
        )
    }

    @Test
    fun whenIncomingGroupCall_withNonExistingCall_ThenSaveCallToDatabase() = runTest {
        // given
        val (arrangement, callRepository) = Arrangement()
            .givenObserveConversationDetailsByIdReturns(
                flowOf(
                    Either.Right(
                        ConversationDetails.Group(
                            Arrangement.groupConversation,
                            isSelfUserMember = true,
                            selfRole = Conversation.Member.Role.Member
                        )
                    )
                )
            )
            .givenGetKnownUserSucceeds()
            .givenGetTeamSucceeds()
            .givenGetCallStatusByConversationIdReturns(null)
            .givenInsertCallSucceeds()
            .arrange()

        // when
        callRepository.createCall(
            conversationId = Arrangement.conversationId,
            status = CallStatus.INCOMING,
            callerId = callerId,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationTypeForCall.Conference
        )

        // then
        coVerify {
            arrangement.callDAO.insertCall(any())
        }.wasInvoked(exactly = once)

        assertTrue(
            callRepository.getCallMetadataProfile().data.containsKey(Arrangement.conversationId)
        )
    }

    @Test
    fun whenIncomingGroupCall_withExistingCallMetadata_ThenDontSaveCallToDatabase() = runTest {
        // given
        val (arrangement, callRepository) = Arrangement()
            .givenObserveConversationDetailsByIdReturns(
                flowOf(
                    Either.Right(
                        ConversationDetails.Group(
                            Arrangement.groupConversation,
                            isSelfUserMember = true,
                            selfRole = Conversation.Member.Role.Member
                        )
                    )
                )
            )
            .givenGetKnownUserSucceeds()
            .givenGetTeamSucceeds()
            .givenGetCallStatusByConversationIdReturns(CallEntity.Status.ESTABLISHED)
            .givenInsertCallSucceeds()
            .arrange()

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    Arrangement.conversationId to createCallMetadata().copy(
                        isMuted = false
                    )
                )
            )
        )

        // when
        callRepository.createCall(
            conversationId = Arrangement.conversationId,
            status = CallStatus.INCOMING,
            callerId = callerId,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationTypeForCall.Conference
        )

        // then
        coVerify {
            arrangement.callDAO.insertCall(any())
        }.wasNotInvoked()

        assertTrue(
            callRepository.getCallMetadataProfile().data.containsKey(Arrangement.conversationId)
        )
    }

    @Test
    fun whenIncomingGroupCall_withNonExistingCallMetadata_ThenUpdateCallInDatabase() = runTest {
        // given
        val callEntity = createCallEntity().copy(
            status = CallEntity.Status.INCOMING
        )

        val (arrangement, callRepository) = Arrangement()
            .givenObserveConversationDetailsByIdReturns(
                flowOf(
                    Either.Right(
                        ConversationDetails.Group(
                            Arrangement.groupConversation,
                            isSelfUserMember = true,
                            selfRole = Conversation.Member.Role.Member
                        )
                    )
                )
            )
            .givenGetKnownUserSucceeds()
            .givenGetTeamSucceeds()
            .givenGetCallStatusByConversationIdReturns(CallEntity.Status.ESTABLISHED)
            .givenInsertCallSucceeds()
            .arrange()

        // when
        callRepository.createCall(
            conversationId = Arrangement.conversationId,
            status = CallStatus.INCOMING,
            callerId = callerId,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationTypeForCall.Conference
        )

        // then
        coVerify {
            arrangement.callDAO.updateLastCallStatusByConversationId(
                eq(CallEntity.Status.STILL_ONGOING),
                eq(callEntity.conversationId)
            )
        }.wasInvoked(exactly = once)

        assertTrue(
            callRepository.getCallMetadataProfile().data.containsKey(Arrangement.conversationId)
        )
    }

    @Test
    fun whenStartingAOneOnOneCall_withNoExistingCall_ThenSaveCallToDatabase() = runTest {
        // given
        val (arrangement, callRepository) = Arrangement()
            .givenObserveConversationDetailsByIdReturns(flowOf(Either.Right(Arrangement.oneOnOneConversationDetails)))
            .givenGetKnownUserSucceeds()
            .givenGetTeamSucceeds()
            .givenGetCallStatusByConversationIdReturns(null)
            .givenInsertCallSucceeds()
            .arrange()

        // when
        callRepository.createCall(
            conversationId = Arrangement.conversationId,
            status = CallStatus.STARTED,
            callerId = callerId,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationTypeForCall.OneOnOne
        )

        // then
        coVerify {
            arrangement.callDAO.insertCall(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun whenStartingAOneOnOneCall_withExistingClosedCall_ThenSaveCallToDatabase() = runTest {
        // given
        val (arrangement, callRepository) = Arrangement()
            .givenObserveConversationDetailsByIdReturns(flowOf(Either.Right(Arrangement.oneOnOneConversationDetails)))
            .givenGetKnownUserSucceeds()
            .givenGetTeamSucceeds()
            .givenGetCallStatusByConversationIdReturns(CallEntity.Status.CLOSED)
            .givenPersistMessageSuccessful()
            .givenInsertCallSucceeds()
            .arrange()

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    Arrangement.conversationId to createCallMetadata().copy(
                        isMuted = false
                    )
                )
            )
        )

        // when
        callRepository.createCall(
            conversationId = Arrangement.conversationId,
            status = CallStatus.STARTED,
            callerId = callerId,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationTypeForCall.OneOnOne
        )

        // then
        coVerify {
            arrangement.callDAO.insertCall(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.persistMessage.invoke(any())
        }.wasNotInvoked()

        assertEquals(
            true,
            callRepository.getCallMetadataProfile().data[Arrangement.conversationId]?.isMuted
        )
    }

    @Test
    fun whenIncomingOneOnOneCall_withNonExistingCall_ThenSaveCallToDatabase() = runTest {
        // given
        val (arrangement, callRepository) = Arrangement()
            .givenObserveConversationDetailsByIdReturns(flowOf(Either.Right(Arrangement.oneOnOneConversationDetails)))
            .givenGetKnownUserSucceeds()
            .givenGetTeamSucceeds()
            .givenGetCallStatusByConversationIdReturns(null)
            .givenPersistMessageSuccessful()
            .givenInsertCallSucceeds()
            .arrange()

        // when
        callRepository.createCall(
            conversationId = Arrangement.conversationId,
            status = CallStatus.INCOMING,
            callerId = callerId,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationTypeForCall.OneOnOne
        )

        coVerify {
            arrangement.persistMessage.invoke(any())
        }.wasNotInvoked()

        coVerify {
            arrangement.callDAO.insertCall(any())
        }.wasInvoked(exactly = once)

        assertTrue(
            callRepository.getCallMetadataProfile().data.containsKey(Arrangement.conversationId)
        )
    }

    @Test
    fun whenIncomingOneOnOneCall_withExistingCallMetadata_ThenDontSaveCallToDatabase() = runTest {
        // given
        val (arrangement, callRepository) = Arrangement()
            .givenObserveConversationDetailsByIdReturns(flowOf(Either.Right(Arrangement.oneOnOneConversationDetails)))
            .givenGetKnownUserSucceeds()
            .givenGetTeamSucceeds()
            .givenGetCallStatusByConversationIdReturns(CallEntity.Status.ESTABLISHED)
            .givenPersistMessageSuccessful()
            .givenInsertCallSucceeds()
            .arrange()

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    Arrangement.conversationId to createCallMetadata().copy(
                        isMuted = false
                    )
                )
            )
        )

        // when
        callRepository.createCall(
            conversationId = Arrangement.conversationId,
            status = CallStatus.INCOMING,
            callerId = callerId,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationTypeForCall.OneOnOne
        )

        // then
        coVerify {
            arrangement.callDAO.insertCall(any())
        }.wasNotInvoked()

        coVerify {
            arrangement.persistMessage.invoke(any())
        }.wasNotInvoked()

        assertTrue(
            callRepository.getCallMetadataProfile().data.containsKey(Arrangement.conversationId)
        )
    }

    @Test
    fun whenIncomingOneOnOneCall_withNonExistingCallMetadata_ThenUpdateCallMetadata() = runTest {
        // given
        val (arrangement, callRepository) = Arrangement()
            .givenObserveConversationDetailsByIdReturns(flowOf(Either.Right(Arrangement.oneOnOneConversationDetails)))
            .givenGetKnownUserSucceeds()
            .givenGetTeamSucceeds()
            .givenGetCallStatusByConversationIdReturns(CallEntity.Status.ESTABLISHED)
            .givenGetCallerIdByConversationIdReturns("callerId@domain")
            .givenInsertCallSucceeds()
            .arrange()

        // when
        callRepository.createCall(
            conversationId = Arrangement.conversationId,
            status = CallStatus.INCOMING,
            callerId = callerId,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationTypeForCall.OneOnOne
        )

        // then
        coVerify {
            arrangement.callDAO.updateLastCallStatusByConversationId(eq(CallEntity.Status.CLOSED), eq(Arrangement.conversationId.toDao()))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.callDAO.insertCall(any())
        }.wasInvoked(exactly = once)

        assertTrue(
            callRepository.getCallMetadataProfile().data.containsKey(Arrangement.conversationId)
        )
    }

    @Test
    fun givenAConversationIdThatExistsInTheFlow_whenUpdateCallStatus_thenUpdateCallStatusIsCalledCorrectly() = runTest {
        // given
        val callEntity = createCallEntity()
        val (arrangement, callRepository) = Arrangement().arrange()

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    Arrangement.conversationId to createCallMetadata().copy(
                        isMuted = false
                    )
                )
            )
        )

        // when
        callRepository.updateCallStatusById(Arrangement.conversationId, CallStatus.ESTABLISHED)

        // then
        coVerify {
            arrangement.callDAO.updateLastCallStatusByConversationId(
                eq(CallEntity.Status.ESTABLISHED),
                eq(callEntity.conversationId)
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationIdThatDoesNotExistsInTheFlow_whenUpdateCallStatusIsCalled_thenUpdateTheStatus() = runTest {
        val (arrangement, callRepository) = Arrangement().arrange()

        callRepository.updateCallStatusById(Arrangement.randomConversationId, CallStatus.INCOMING)

        coVerify {
            arrangement.callDAO.updateLastCallStatusByConversationId(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationIdThatDoesNotExistsInTheFlow_whenUpdateIsMutedByIdIsCalled_thenDoNotUpdateTheFlow() = runTest {
        val (_, callRepository) = Arrangement().arrange()

        callRepository.updateIsMutedById(Arrangement.randomConversationId, false)

        assertFalse {
            callRepository.getCallMetadataProfile().data.containsKey(Arrangement.randomConversationId)
        }
    }

    @Test
    fun givenAConversationIdThatExistsInTheFlow_whenUpdateIsMutedByIdIsCalled_thenUpdateCallStatusInTheFlow() = runTest {
        // given
        val (_, callRepository) = Arrangement().arrange()
        val expectedValue = false
        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    Arrangement.conversationId to createCallMetadata().copy(
                        isMuted = true
                    )
                )
            )
        )

        // when
        callRepository.updateIsMutedById(Arrangement.conversationId, expectedValue)

        // then
        assertEquals(
            expectedValue,
            callRepository.getCallMetadataProfile().data[Arrangement.conversationId]?.isMuted
        )
    }

    @Test
    fun givenAnEstablishedCall_whenUpdateIsCbrEnabledIsCalled_thenDoUpdateCbrValue() = runTest {
        val call = createCallEntity()
        val (_, callRepository) = Arrangement().givenEstablishedCall(call).arrange()
        val expectedValue = true

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    Arrangement.conversationId to createCallMetadata().copy(
                        isCbrEnabled = false
                    )
                )
            )
        )

        callRepository.updateIsCbrEnabled(expectedValue)

        assertEquals(
            expectedValue,
            callRepository.getCallMetadataProfile().data[Arrangement.conversationId]?.isCbrEnabled
        )
    }

    @Test
    fun givenAConversationIdThatDoesNotExistsInTheFlow_whenUpdateIsCameraOnByIdIsCalled_thenDoNotUpdateTheFlow() = runTest {
        val (_, callRepository) = Arrangement().arrange()
        callRepository.updateIsCameraOnById(Arrangement.randomConversationId, false)

        assertFalse {
            callRepository.getCallMetadataProfile().data.containsKey(Arrangement.randomConversationId)
        }
    }

    @Test
    fun givenAConversationIdThatExistsInTheFlow_whenUpdateIsCameraOnByIdIsCalled_thenUpdateCallStatusInTheFlow() = runTest {
        // given
        val (_, callRepository) = Arrangement().arrange()
        val expectedValue = false
        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    Arrangement.conversationId to createCallMetadata().copy(
                        isCameraOn = true
                    )
                )
            )
        )

        // when
        callRepository.updateIsCameraOnById(Arrangement.conversationId, expectedValue)

        // then
        assertEquals(
            expectedValue,
            callRepository.getCallMetadataProfile().data[Arrangement.conversationId]?.isCameraOn
        )
    }

    @Test
    fun givenAConversationIdThatDoesNotExistsInTheFlow_whenUpdateCallParticipantsIsCalled_thenDoNotUpdateTheFlow() = runTest {
        val (_, callRepository) = Arrangement().arrange()
        callRepository.updateCallParticipants(
            Arrangement.randomConversationId,
            emptyList()
        )

        assertFalse {
            callRepository.getCallMetadataProfile().data.containsKey(Arrangement.randomConversationId)
        }
    }

    @Test
    fun givenAConversationIdThatExistsInTheFlow_whenUpdateCallParticipantsIsCalled_thenUpdateCallStatusInTheFlow() = runTest {
        // given
        val (_, callRepository) = Arrangement()
            .givenGetKnownUserMinimizedSucceeds()
            .arrange()
        val participantsList = listOf(
            ParticipantMinimized(
                id = QualifiedID("participantId", ""),
                userId = QualifiedID("participantId", "participantDomain"),
                clientId = "abcd",
                isMuted = true,
                isCameraOn = false,
                isSharingScreen = false,
                hasEstablishedAudio = true
            )
        )
        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    Arrangement.conversationId to createCallMetadata().copy(
                        participants = emptyList(),
                        maxParticipants = 0
                    )
                )
            )
        )

        // when
        callRepository.updateCallParticipants(
            Arrangement.conversationId,
            participantsList
        )

        // then
        val metadata = callRepository.getCallMetadataProfile().data[Arrangement.conversationId]
        assertEquals(
            participantsList,
            metadata?.participants
        )
    }

    @Test
    fun givenCallWithSomeParticipants_whenUpdateCallParticipantsIsCalledWithNewParticipants_thenOnlyNewUsersFetchedFromDB() =
        runTest {
            // given
            val (arrangement, callRepository) = Arrangement()
                .givenGetKnownUserMinimizedSucceeds()
                .arrange()
            val participant = ParticipantMinimized(
                id = QualifiedID("participantId", ""),
                userId = QualifiedID("participantId", "participantDomain"),
                clientId = "abcd",
                isMuted = true,
                isCameraOn = false,
                isSharingScreen = false,
                hasEstablishedAudio = true
            )
            val newParticipant = participant.copy(userId = QualifiedID("anotherParticipantId", "participantDomain"))
            val participantsList = listOf(participant)
            callRepository.updateCallMetadataProfileFlow(
                callMetadataProfile = CallMetadataProfile(
                    data = mapOf(
                        Arrangement.conversationId to createCallMetadata().copy(
                            participants = participantsList,
                            maxParticipants = 0
                        )
                    )
                )
            )

            // when
            callRepository.updateCallParticipants(
                Arrangement.conversationId,
                participantsList.plus(newParticipant)
            )

            // then
            coVerify {
                arrangement.userRepository.getUsersMinimizedByQualifiedIDs(listOf(newParticipant.userId))
            }.wasInvoked(exactly = once)
        }

    @Test
    fun givenCallWithSomeParticipants_whenUpdateCallParticipantsIsCalledWithSameParticipants_thenNoFetchingUsersFromDB() =
        runTest {
            // given
            val (arrangement, callRepository) = Arrangement()
                .givenGetKnownUserMinimizedSucceeds()
                .arrange()
            val participant = ParticipantMinimized(
                id = QualifiedID("participantId", ""),
                userId = QualifiedID("participantId", "participantDomain"),
                clientId = "abcd",
                isMuted = true,
                isCameraOn = false,
                isSharingScreen = false,
                hasEstablishedAudio = true
            )
            val otherParticipant = participant.copy(id = QualifiedID("anotherParticipantId", "participantDomain"))
            val participantsList = listOf(participant, otherParticipant)
            callRepository.updateCallMetadataProfileFlow(
                callMetadataProfile = CallMetadataProfile(
                    data = mapOf(
                        Arrangement.conversationId to createCallMetadata().copy(
                            participants = participantsList,
                            maxParticipants = 0
                        )
                    )
                )
            )

            // when
            callRepository.updateCallParticipants(
                Arrangement.conversationId,
                participantsList
            )

            // then
            coVerify {
                arrangement.userRepository.getUsersMinimizedByQualifiedIDs(listOf(otherParticipant.id))
            }.wasNotInvoked()
        }

    @Test
    fun givenAConversationIdThatDoesNotExistsInTheFlow_whenUpdateParticipantsActiveSpeakerIsCalled_thenDoNotUpdateTheFlow() = runTest {
        val (_, callRepository) = Arrangement().arrange()
        callRepository.updateParticipantsActiveSpeaker(
            Arrangement.randomConversationId,
            emptyMap()
        )

        assertFalse {
            callRepository.getCallMetadataProfile().data.containsKey(Arrangement.randomConversationId)
        }
    }


    @Test
    fun givenAnIncomingCall_whenRequestingIncomingCalls_thenReturnTheIncomingCall() = runTest {
        // given
        val expectedCall = provideCall(
            id = Arrangement.conversationId,
            status = CallStatus.INCOMING
        )
        val callEntity = createCallEntity().copy(
            status = CallEntity.Status.INCOMING,
            callerId = "callerId@domain",
            conversationType = ConversationEntity.Type.ONE_ON_ONE
        )
        val (_, callRepository) = Arrangement()
            .givenObserveIncomingCallsReturns(flowOf(listOf(callEntity)))
            .arrange()

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    Arrangement.conversationId to createCallMetadata().copy(
                        isMuted = false,
                        conversationName = "ONE_ON_ONE Name",
                        conversationType = Conversation.Type.ONE_ON_ONE,
                        callerName = "otherUsername",
                        callerTeamName = "team_1"
                    )
                )
            )
        )

        // when
        val incomingCalls = callRepository.incomingCallsFlow()

        // then
        incomingCalls.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(
                expectedCall,
                list[0]
            )
        }
    }

    @Test
    fun givenAnOngoingCall_whenRequestingOngoingCalls_thenReturnTheOngoingCall() = runTest {
        // given
        val callEntity = createCallEntity().copy(
            status = CallEntity.Status.STILL_ONGOING,
            callerId = "callerId@domain",
            conversationType = ConversationEntity.Type.ONE_ON_ONE
        )
        val (_, callRepository) = Arrangement()
            .givenObserveOngoingCallsReturns(flowOf(listOf(callEntity)))
            .arrange()

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    Arrangement.conversationId to createCallMetadata().copy(
                        isMuted = false,
                        conversationName = "ONE_ON_ONE Name",
                        conversationType = Conversation.Type.ONE_ON_ONE,
                        callerName = "otherUsername",
                        callerTeamName = "team_1"
                    )
                )
            )
        )

        val expectedCall = provideCall(
            id = Arrangement.conversationId,
            status = CallStatus.STILL_ONGOING
        )

        // when
        val ongoingCalls = callRepository.ongoingCallsFlow()

        // then
        ongoingCalls.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(
                expectedCall,
                list[0]
            )
        }
    }

    @Test
    fun givenAnEstablishedCall_whenRequestingEstablishedCalls_thenReturnTheEstablishedCall() = runTest {
        // given
        val callEntity = createCallEntity().copy(
            status = CallEntity.Status.ESTABLISHED,
            callerId = "callerId@domain",
            conversationType = ConversationEntity.Type.ONE_ON_ONE
        )
        val (_, callRepository) = Arrangement()
            .givenObserveEstablishedCallsReturns(flowOf(listOf(callEntity)))
            .arrange()

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    Arrangement.conversationId to createCallMetadata().copy(
                        isMuted = false,
                        conversationName = "ONE_ON_ONE Name",
                        conversationType = Conversation.Type.ONE_ON_ONE,
                        callerName = "otherUsername",
                        callerTeamName = "team_1"
                    )
                )
            )
        )

        val expectedCall = provideCall(
            id = Arrangement.conversationId,
            status = CallStatus.ESTABLISHED
        )

        // when
        val establishedCalls = callRepository.establishedCallsFlow()

        // then
        establishedCalls.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(
                expectedCall,
                list[0]
            )
        }
    }

    @Suppress("LongMethod")
    @Test
    fun givenSomeCalls_whenRequestingCalls_thenReturnTheCalls() = runTest {
        // given
        val missedCall = createCallEntity().copy(
            status = CallEntity.Status.MISSED,
            callerId = "callerId@domain",
            conversationType = ConversationEntity.Type.ONE_ON_ONE
        )

        val closedCall = createCallEntity().copy(
            conversationId = QualifiedIDEntity(
                value = Arrangement.randomConversationId.value,
                domain = Arrangement.randomConversationId.domain
            ),
            status = CallEntity.Status.CLOSED,
            callerId = "callerId@domain",
            conversationType = ConversationEntity.Type.ONE_ON_ONE
        )

        val (_, callRepository) = Arrangement()
            .givenObserveCallsReturns(flowOf(listOf(missedCall, closedCall)))
            .arrange()

        val metadata = createCallMetadata().copy(
            isMuted = false,
            conversationName = "ONE_ON_ONE Name",
            conversationType = Conversation.Type.ONE_ON_ONE,
            callerName = "otherUsername",
            callerTeamName = "team_1"
        )
        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    Arrangement.conversationId to metadata,
                    Arrangement.randomConversationId to metadata.copy(
                        conversationName = "CLOSED CALL"
                    )
                )
            )
        )

        val expectedMissedCall = provideCall(
            id = Arrangement.conversationId,
            status = CallStatus.MISSED
        )

        val expectedClosedCall = provideCall(
            id = Arrangement.randomConversationId,
            status = CallStatus.CLOSED
        ).copy(
            conversationName = "CLOSED CALL"
        )

        // when
        val establishedCalls = callRepository.callsFlow()

        // then
        establishedCalls.test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertEquals(
                expectedMissedCall,
                list[0]
            )
            assertEquals(
                expectedClosedCall,
                list[1]
            )
        }
    }

    @Test
    fun givenAnEstablishedCall_whenRequestingEstablishedCallConversationId_thenReturnTheEstablishedCallConversationId() = runTest {
        // given
        val callEntity = createCallEntity().copy(
            status = CallEntity.Status.ESTABLISHED,
            callerId = "callerId@domain",
            conversationType = ConversationEntity.Type.ONE_ON_ONE
        )
        val (_, callRepository) = Arrangement()
            .givenObserveEstablishedCallsReturns(flowOf(listOf(callEntity)))
            .arrange()

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    Arrangement.conversationId to createCallMetadata().copy(
                        isMuted = false,
                        conversationName = "ONE_ON_ONE Name",
                        conversationType = Conversation.Type.ONE_ON_ONE,
                        callerName = "otherUsername",
                        callerTeamName = "team_1"
                    )
                )
            )
        )

        // when
        val establishedCallConversationId = callRepository.establishedCallConversationId()

        // then
        assertEquals(
            Arrangement.conversationId,
            establishedCallConversationId
        )
    }

    @Test
    fun givenAMissedCall_whenPersistMissedCallInvoked_thenStoreTheMissedCallInDatabase() = runTest {
        val (arrangement, callRepository) = Arrangement()
            .givenGetCallerIdByConversationIdReturns(Arrangement.callerIdString)
            .givenPersistMessageSuccessful()
            .arrange()

        callRepository.persistMissedCall(Arrangement.conversationId)

        coVerify {
            arrangement.callDAO.getCallerIdByConversationId(eq(Arrangement.conversationId.toDao()))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.persistMessage.invoke(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAMissedCallAndNoCallerId_whenPersistMissedCallInvoked_thenDontStoreMissedCallInDatabase() = runTest {

        val (arrangement, callRepository) = Arrangement()
            .givenGetCallerIdByConversationIdReturns(null)
            .givenPersistMessageSuccessful()
            .arrange()

        callRepository.persistMissedCall(Arrangement.conversationId)

        coVerify {
            arrangement.callDAO.getCallerIdByConversationId(eq(Arrangement.conversationId.toDao()))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.persistMessage.invoke(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenMlsConferenceCall_whenJoinMlsConference_thenJoinSubconversation() = runTest {
        var hasJoined = false
        val (arrangement, callRepository) = Arrangement()
            .givenGetConversationProtocolInfoReturns(Arrangement.mlsProtocolInfo)
            .givenJoinSubconversationSuccessful()
            .givenObserveEpochChangesReturns(emptyFlow())
            .givenGetSubconversationInfoReturns(Arrangement.subconversationGroupId)
            .givenGetMLSClientSucceeds()
            .givenGetMlsEpochReturns(1UL)
            .givenMlsMembersReturns(emptyList())
            .givenDeriveSecretSuccessful()
            .arrange()

        callRepository.joinMlsConference(Arrangement.conversationId, {
            hasJoined = true
        }) { _, _ -> }

        coVerify {
            arrangement.joinSubconversationUseCase.invoke(eq(Arrangement.conversationId), eq(CALL_SUBCONVERSATION_ID))
        }.wasInvoked(exactly = once)
        assertTrue { hasJoined }
    }

    @Test
    fun givenJoinSubconversationSuccessful_whenJoinMlsConference_thenStartObservingEpoch() = runTest(
        TestKaliumDispatcher.default
    ) {
        val (arrangement, callRepository) = Arrangement()
            .givenGetConversationProtocolInfoReturns(Arrangement.mlsProtocolInfo)
            .givenJoinSubconversationSuccessful()
            .givenObserveEpochChangesReturns(emptyFlow())
            .givenGetSubconversationInfoReturns(Arrangement.subconversationGroupId)
            .givenGetMLSClientSucceeds()
            .givenGetMlsEpochReturns(1UL)
            .givenMlsMembersReturns(emptyList())
            .givenDeriveSecretSuccessful()
            .arrange()

        var onEpochChangeCallCount = 0
        callRepository.joinMlsConference(Arrangement.conversationId, {}) { _, _ ->
            onEpochChangeCallCount += 1
        }
        yield()
        advanceUntilIdle()

        verify {
            arrangement.epochChangesObserver.observe()
        }.wasInvoked(exactly = once)

        assertEquals(1, onEpochChangeCallCount)
    }

    @Test
    fun givenEpochChange_whenJoinMlsConference_thenInvokeOnEpochChange() = runTest(TestKaliumDispatcher.default) {

        val epochFlow = MutableSharedFlow<GroupID>()

        val (_, callRepository) = Arrangement()
            .givenGetConversationProtocolInfoReturns(Arrangement.mlsProtocolInfo)
            .givenJoinSubconversationSuccessful()
            .givenObserveEpochChangesReturns(epochFlow)
            .givenGetSubconversationInfoReturns(Arrangement.subconversationGroupId)
            .givenGetMLSClientSucceeds()
            .givenGetMlsEpochReturns(1UL)
            .givenMlsMembersReturns(emptyList())
            .givenDeriveSecretSuccessful()
            .arrange()

        var onEpochChangeCallCount = 0
        callRepository.joinMlsConference(Arrangement.conversationId, {}) { _, _ ->
            onEpochChangeCallCount += 1
        }
        yield()
        advanceUntilIdle()

        epochFlow.emit(Arrangement.groupId)
        yield()
        advanceUntilIdle()

        epochFlow.emit(Arrangement.subconversationGroupId)
        yield()
        advanceUntilIdle()

        assertEquals(3, onEpochChangeCallCount)
    }

    @Test
    fun givenMlsConferenceCall_whenLeaveMlsConference_thenEpochObservingStops() = runTest(TestKaliumDispatcher.default) {
        val epochFlow = MutableSharedFlow<GroupID>()

        val (_, callRepository) = Arrangement()
            .givenGetConversationProtocolInfoReturns(Arrangement.mlsProtocolInfo)
            .givenJoinSubconversationSuccessful()
            .givenObserveEpochChangesReturns(epochFlow)
            .givenGetSubconversationInfoReturns(Arrangement.subconversationGroupId)
            .givenGetMLSClientSucceeds()
            .givenGetMlsEpochReturns(1UL)
            .givenMlsMembersReturns(emptyList())
            .givenDeriveSecretSuccessful()
            .givenLeaveSubconversationSuccessful()
            .arrange()

        var onEpochChangeCallCount = 0
        callRepository.joinMlsConference(Arrangement.conversationId, {}) { _, _ ->
            onEpochChangeCallCount += 1
        }
        yield()
        advanceUntilIdle()

        callRepository.leaveMlsConference(Arrangement.conversationId)
        yield()
        advanceUntilIdle()

        epochFlow.emit(Arrangement.subconversationGroupId)
        yield()
        advanceUntilIdle()

        assertEquals(1, onEpochChangeCallCount)
    }

    @Test
    fun givenMlsConferenceCall_whenLeaveMlsConference_thenLeaveSubconversation() = runTest(TestKaliumDispatcher.default) {
        val epochFlow = MutableSharedFlow<GroupID>()

        val (arrangement, callRepository) = Arrangement()
            .givenGetConversationProtocolInfoReturns(Arrangement.mlsProtocolInfo)
            .givenJoinSubconversationSuccessful()
            .givenObserveEpochChangesReturns(epochFlow)
            .givenGetSubconversationInfoReturns(Arrangement.subconversationGroupId)
            .givenGetMLSClientSucceeds()
            .givenGetMlsEpochReturns(1UL)
            .givenMlsMembersReturns(emptyList())
            .givenDeriveSecretSuccessful()
            .givenLeaveSubconversationSuccessful()
            .arrange()

        callRepository.joinMlsConference(Arrangement.conversationId, {}) { _, _ -> }
        yield()
        advanceUntilIdle()

        callRepository.leaveMlsConference(Arrangement.conversationId)
        yield()
        advanceUntilIdle()

        coVerify {
            arrangement.leaveSubconversationUseCase.invoke(eq(Arrangement.conversationId), eq(CALL_SUBCONVERSATION_ID))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsConferenceCall_whenAdvanceEpoch_thenKeyMaterialIsUpdatedInSubconversation() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, callRepository) = Arrangement()
            .givenGetSubconversationInfoReturns(Arrangement.subconversationGroupId)
            .givenUpdateKeyMaterialSucceeds()
            .arrange()

        callRepository.advanceEpoch(Arrangement.conversationId)

        coVerify {
            arrangement.mlsConversationRepository.updateKeyingMaterial(eq(Arrangement.subconversationGroupId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsConferenceCall_whenParticipantStaysUnconnected_thenParticipantGetRemovedFromSubconversation() = runTest(
        TestKaliumDispatcher.main
    ) {
        val (arrangement, callRepository) = Arrangement()
            .givenGetKnownUserMinimizedSucceeds()
            .givenGetSubconversationInfoReturns(Arrangement.subconversationGroupId)
            .givenRemoveClientsFromMLSGroupSucceeds()
            .arrange()

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    Arrangement.conversationId to createCallMetadata().copy(
                        protocol = Arrangement.mlsProtocolInfo,
                        maxParticipants = 0
                    )
                )
            )
        )
        callRepository.updateCallParticipants(
            Arrangement.conversationId,
            listOf(
                participant.copy(
                    hasEstablishedAudio = false
                )
            )
        )
        advanceTimeBy(CallDataSource.STALE_PARTICIPANT_TIMEOUT.toLong(DurationUnit.MILLISECONDS))
        yield()

        coVerify {
            arrangement.mlsConversationRepository.removeClientsFromMLSGroup(
                groupID = eq(Arrangement.subconversationGroupId),
                clientIdList = eq(listOf(Arrangement.qualifiedClientID))
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsConferenceCall_whenParticipantReconnects_thenParticipantIsNotRemovedFromSubconversation() = runTest(
        TestKaliumDispatcher.main
    ) {
        val (arrangement, callRepository) = Arrangement()
            .givenGetSubconversationInfoReturns(Arrangement.subconversationGroupId)
            .givenRemoveClientsFromMLSGroupSucceeds()
            .givenGetKnownUserMinimizedSucceeds()
            .arrange()

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    Arrangement.conversationId to createCallMetadata().copy(
                        protocol = Arrangement.mlsProtocolInfo,
                        maxParticipants = 0
                    )
                )
            )
        )
        callRepository.updateCallParticipants(
            Arrangement.conversationId,
            listOf(
                Arrangement.participant.copy(
                    hasEstablishedAudio = false
                )
            )
        )
        advanceTimeBy(
            CallDataSource.STALE_PARTICIPANT_TIMEOUT.minus(1.toDuration(DurationUnit.SECONDS)).toLong(
                DurationUnit.MILLISECONDS
            )
        )
        yield()

        callRepository.updateCallParticipants(
            Arrangement.conversationId,
            listOf(
                Arrangement.participant.copy(
                    hasEstablishedAudio = true
                )
            )
        )
        advanceTimeBy(CallDataSource.STALE_PARTICIPANT_TIMEOUT.toLong(DurationUnit.MILLISECONDS))
        yield()

        coVerify {
            arrangement.mlsConversationRepository.removeClientsFromMLSGroup(
                groupID = eq(Arrangement.subconversationGroupId),
                clientIdList = eq(listOf(Arrangement.qualifiedClientID))
            )
        }.wasNotInvoked()
    }

    @Test
    fun givenMlsConferenceCall_whenClosingOpenCalls_thenAttemptToLeaveMlsConference() = runTest {
        // given
        val callEntity = createCallEntity().copy(
            status = CallEntity.Status.ESTABLISHED,
            callerId = "callerId@domain",
            type = CallEntity.Type.MLS_CONFERENCE
        )
        val (arrangement, callRepository) = Arrangement()
            .givenObserveEstablishedCallsReturns(flowOf(listOf(callEntity)))
            .givenLeaveSubconversationSuccessful()
            .arrange()

        // when
        callRepository.updateOpenCallsToClosedStatus()
        yield()
        advanceUntilIdle()

        // then
        coVerify {
            arrangement.leaveSubconversationUseCase.invoke(eq(Arrangement.conversationId), eq(CALL_SUBCONVERSATION_ID))
        }.wasInvoked(exactly = once)

    }

    @Test
    fun givenAConversationIdThatExistsInTheFlow_whenUpdateParticipantsActiveSpeakerIsCalled_thenUpdateTheFlow() = runTest {
        val (_, callRepository) = Arrangement().arrange()
        val activeSpeakers = mapOf(QualifiedID("participantId", "participantDomain") to listOf("abcd"))

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    Arrangement.conversationId to createCallMetadata().copy(
                        participants = emptyList(),
                        maxParticipants = 0
                    )
                )
            )
        )

        callRepository.updateParticipantsActiveSpeaker(
            Arrangement.conversationId,
            activeSpeakers
        )

        assertEquals(activeSpeakers, callRepository.getCallMetadataProfile().data[Arrangement.conversationId]?.activeSpeakers)
    }

    @Test
    fun givenCallWithActiveSpeakers_whenGetFullParticipants_thenOnlySpeakingUsers() = runTest {
        val (_, callRepository) = Arrangement().arrange()
        val mutedParticipant = ParticipantMinimized(
            id = QualifiedID("participantId", ""),
            userId = QualifiedID("participantId", "participantDomain"),
            clientId = "abcd0",
            isMuted = true,
            isCameraOn = false,
            isSharingScreen = false,
            hasEstablishedAudio = true
        )
        val unMutedParticipant = mutedParticipant.copy(
            id = QualifiedID("anotherParticipantId", ""),
            userId = QualifiedID("anotherParticipantId", "participantDomain"),
            clientId = "abcd1",
            isMuted = false
        )
        val activeSpeakers = mapOf(
            mutedParticipant.userId to listOf(mutedParticipant.clientId),
            unMutedParticipant.userId to listOf(unMutedParticipant.clientId),
        )

        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(
                    Arrangement.conversationId to createCallMetadata().copy(
                        participants = listOf(mutedParticipant, unMutedParticipant),
                        maxParticipants = 0
                    )
                )
            )
        )

        // when
        callRepository.updateParticipantsActiveSpeaker(Arrangement.conversationId, activeSpeakers)

        // then
        val fullParticipants = callRepository.getCallMetadataProfile().data[Arrangement.conversationId]?.getFullParticipants()

        assertEquals(
            false,
            fullParticipants?.first { it.id == mutedParticipant.id && it.clientId == mutedParticipant.clientId }?.isSpeaking
        )
        assertEquals(
            true,
            fullParticipants?.first { it.id == unMutedParticipant.id && it.clientId == unMutedParticipant.clientId }?.isSpeaking
        )
    }

    @Test
    fun givenCallWithParticipantsNotSharingScreen_whenOneStartsToShare_thenSharingMetadataHasProperValues() = runTest {
        // given
        val otherParticipant = participant.copy(id = QualifiedID("anotherParticipantId", "participantDomain"))
        val participantsList = listOf(participant, otherParticipant)
        val (_, callRepository) = Arrangement()
            .givenGetKnownUserMinimizedSucceeds()
            .arrange()
        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(Arrangement.conversationId to createCallMetadata().copy(participants = participantsList))
            )
        )

        // when
        callRepository.updateCallParticipants(
            Arrangement.conversationId,
            listOf(participant, otherParticipant.copy(isSharingScreen = true))
        )
        val callMetadata = callRepository.getCallMetadataProfile()[Arrangement.conversationId]

        // then
        assertNotNull(callMetadata)
        assertEquals(0L, callMetadata.screenShareMetadata.completedScreenShareDurationInMillis)
        assertTrue(callMetadata.screenShareMetadata.activeScreenShares.containsKey(otherParticipant.id))
    }

    @Test
    fun givenCallWithParticipantsNotSharingScreen_whenTwoStartsAndOneStops_thenSharingMetadataHasProperValues() = runTest {
        // given
        val (_, callRepository) = Arrangement()
            .arrange()
        val secondParticipant = participant.copy(id = QualifiedID("secondParticipantId", "participantDomain"))
        val thirdParticipant = participant.copy(id = QualifiedID("thirdParticipantId", "participantDomain"))
        val participantsList = listOf(participant, secondParticipant, thirdParticipant)
        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(Arrangement.conversationId to createCallMetadata().copy(participants = participantsList))
            )
        )

        // when
        callRepository.updateCallParticipants(
            Arrangement.conversationId,
            listOf(participant, secondParticipant.copy(isSharingScreen = true), thirdParticipant.copy(isSharingScreen = true))
        )
        callRepository.updateCallParticipants(
            Arrangement.conversationId,
            listOf(participant, secondParticipant, thirdParticipant.copy(isSharingScreen = true))
        )
        val callMetadata = callRepository.getCallMetadataProfile()[Arrangement.conversationId]

        // then
        assertNotNull(callMetadata)
        assertTrue(callMetadata.screenShareMetadata.activeScreenShares.size == 1)
        assertTrue(callMetadata.screenShareMetadata.activeScreenShares.containsKey(thirdParticipant.id))
    }

    @Test
    fun givenCallWithParticipantsSharingScreen_whenOneStopsToShare_thenSharingMetadataHasProperValues() = runTest {
        // given
        val (_, callRepository) = Arrangement()
            .arrange()
        val otherParticipant = participant.copy(id = QualifiedID("anotherParticipantId", "participantDomain"))
        val participantsList = listOf(participant, otherParticipant)
        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(Arrangement.conversationId to createCallMetadata().copy(participants = participantsList))
            )
        )
        callRepository.updateCallParticipants(
            Arrangement.conversationId,
            listOf(participant, otherParticipant.copy(isSharingScreen = true))
        )

        // when
        callRepository.updateCallParticipants(
            Arrangement.conversationId,
            listOf(participant, otherParticipant.copy(isSharingScreen = false))
        )
        val callMetadata = callRepository.getCallMetadataProfile()[Arrangement.conversationId]

        // then
        assertNotNull(callMetadata)
        assertTrue(callMetadata.screenShareMetadata.activeScreenShares.isEmpty())
    }

    @Test
    fun givenCallWithParticipantsSharingScreen_whenTheSameParticipantIsSharingMultipleTime_thenSharingMetadataHasUserIdOnlyOnce() =
        runTest {
            // given
            val (_, callRepository) = Arrangement()
                .arrange()
            val otherParticipant = participant.copy(id = QualifiedID("anotherParticipantId", "participantDomain"))
            val participantsList = listOf(participant, otherParticipant)
            callRepository.updateCallMetadataProfileFlow(
                callMetadataProfile = CallMetadataProfile(
                    data = mapOf(Arrangement.conversationId to createCallMetadata().copy(participants = participantsList))
                )
            )

            // when
            callRepository.updateCallParticipants(
                Arrangement.conversationId,
                listOf(participant, otherParticipant.copy(isSharingScreen = true))
            )
            callRepository.updateCallParticipants(
                Arrangement.conversationId,
                listOf(participant, otherParticipant.copy(isSharingScreen = false))
            )
            callRepository.updateCallParticipants(
                Arrangement.conversationId,
                listOf(participant, otherParticipant.copy(isSharingScreen = true))
            )
            val callMetadata = callRepository.getCallMetadataProfile()[Arrangement.conversationId]

            // then
            assertNotNull(callMetadata)
            assertTrue(callMetadata.screenShareMetadata.uniqueSharingUsers.size == 1)
            assertTrue(callMetadata.screenShareMetadata.uniqueSharingUsers.contains(otherParticipant.id.toString()))
        }

    @Test
    fun givenCallWithParticipantsSharingScreen_whenTwoParticipantsAreSharing_thenSharingMetadataHasBothOfUsersIds() = runTest {
        // given
        val (_, callRepository) = Arrangement()
            .arrange()
        val secondParticipant = participant.copy(id = QualifiedID("secondParticipantId", "participantDomain"))
        val thirdParticipant = participant.copy(id = QualifiedID("thirdParticipantId", "participantDomain"))
        val participantsList = listOf(participant, secondParticipant, thirdParticipant)
        callRepository.updateCallMetadataProfileFlow(
            callMetadataProfile = CallMetadataProfile(
                data = mapOf(Arrangement.conversationId to createCallMetadata().copy(participants = participantsList))
            )
        )

        // when
        callRepository.updateCallParticipants(
            Arrangement.conversationId,
            listOf(participant, secondParticipant.copy(isSharingScreen = true), thirdParticipant.copy(isSharingScreen = true))
        )
        val callMetadata = callRepository.getCallMetadataProfile()[Arrangement.conversationId]

        // then
        assertNotNull(callMetadata)
        assertTrue(callMetadata.screenShareMetadata.uniqueSharingUsers.size == 2)
        assertEquals(
            setOf(secondParticipant.id.toString(), thirdParticipant.id.toString()),
            callMetadata.screenShareMetadata.uniqueSharingUsers
        )
    }

    private fun provideCall(id: ConversationId, status: CallStatus) = Call(
        conversationId = id,
        status = status,
        callerId = UserId("callerId", "domain"),
        participants = listOf(),
        isMuted = false,
        isCameraOn = false,
        isCbrEnabled = false,
        maxParticipants = 0,
        conversationName = "ONE_ON_ONE Name",
        conversationType = Conversation.Type.ONE_ON_ONE,
        callerName = "otherUsername",
        callerTeamName = "team_1"
    )

    private fun createCallEntity() = CallEntity(
        conversationId = QualifiedIDEntity(
            value = Arrangement.conversationId.value,
            domain = Arrangement.conversationId.domain
        ),
        id = "abcd-1234",
        status = CallEntity.Status.STARTED,
        callerId = callerId.toString(),
        conversationType = ConversationEntity.Type.GROUP,
        type = CallEntity.Type.CONFERENCE
    )

    private fun createCallMetadata() = CallMetadata(
        callerId = callerId,
        isMuted = true,
        isCameraOn = false,
        isCbrEnabled = false,
        conversationName = null,
        conversationType = Conversation.Type.GROUP,
        callerName = null,
        callerTeamName = null,
        callStatus = CallStatus.ESTABLISHED,
        protocol = Conversation.ProtocolInfo.Proteus,
        activeSpeakers = mapOf()
    )

    private class Arrangement {

        @Mock
        val callApi = mock(CallApi::class)

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val userRepository = mock(UserRepository::class)

        @Mock
        val teamRepository = mock(TeamRepository::class)

        @Mock
        val sessionRepository = mock(SessionRepository::class)

        @Mock
        val qualifiedIdMapper = mock(QualifiedIdMapper::class)

        @Mock
        val persistMessage = mock(PersistMessageUseCase::class)

        @Mock
        val mlsClientProvider = mock(MLSClientProvider::class)

        @Mock
        val mlsClient = mock(MLSClient::class)

        @Mock
        val joinSubconversationUseCase = mock(JoinSubconversationUseCase::class)

        @Mock
        val leaveSubconversationUseCase = mock(LeaveSubconversationUseCase::class)

        @Mock
        val subconversationRepository = mock(SubconversationRepository::class)

        @Mock
        val mlsConversationRepository = mock(MLSConversationRepository::class)

        @Mock
        val epochChangesObserver = mock(EpochChangesObserver::class)

        @Mock
        val callDAO = mock(CallDAO::class)

        private val callMapper = CallMapperImpl(qualifiedIdMapper)
        private val federatedIdMapper = FederatedIdMapperImpl(TestUser.SELF.id, qualifiedIdMapper, sessionRepository)

        private val callRepository: CallRepository = CallDataSource(
            callApi = callApi,
            callDAO = callDAO,
            qualifiedIdMapper = qualifiedIdMapper,
            conversationRepository = conversationRepository,
            subconversationRepository = subconversationRepository,
            mlsConversationRepository = mlsConversationRepository,
            userRepository = userRepository,
            epochChangesObserver = epochChangesObserver,
            teamRepository = teamRepository,
            persistMessage = persistMessage,
            mlsClientProvider = mlsClientProvider,
            joinSubconversation = joinSubconversationUseCase,
            leaveSubconversation = leaveSubconversationUseCase,
            callMapper = callMapper,
            federatedIdMapper = federatedIdMapper,
            kaliumDispatchers = TestKaliumDispatcher
        )

        init {
            every {
                qualifiedIdMapper.fromStringToQualifiedID(eq("convId@domainId"))
            }.returns(QualifiedID("convId", "domainId"))

            every {
                qualifiedIdMapper.fromStringToQualifiedID(eq("random@domain"))
            }.returns(QualifiedID("random", "domain"))

            every {
                qualifiedIdMapper.fromStringToQualifiedID(eq("callerId@domain"))
            }.returns(QualifiedID("callerId", "domain"))

            every {
                qualifiedIdMapper.fromStringToQualifiedID(eq("callerId"))
            }.returns(QualifiedID("callerId", ""))
        }

        fun arrange() = this to callRepository

        fun givenEstablishedCall(callEntity: CallEntity) = apply {
            every {
                callDAO.getEstablishedCall()
            }.returns(callEntity)
        }

        suspend fun givenGetCallConfigResponse(response: NetworkResponse<String>) = apply {
            coEvery {
                callApi.getCallConfig(null)
            }.returns(response)
        }

        suspend fun givenObserveCallsReturns(flow: Flow<List<CallEntity>>) = apply {
            coEvery {
                callDAO.observeCalls()
            }.returns(flow)
        }

        suspend fun givenObserveIncomingCallsReturns(flow: Flow<List<CallEntity>>) = apply {
            coEvery {
                callDAO.observeIncomingCalls()
            }.returns(flow)
        }

        suspend fun givenObserveOngoingCallsReturns(flow: Flow<List<CallEntity>>) = apply {
            coEvery {
                callDAO.observeOngoingCalls()
            }.returns(flow)
        }

        suspend fun givenObserveEstablishedCallsReturns(flow: Flow<List<CallEntity>>) = apply {
            coEvery {
                callDAO.observeEstablishedCalls()
            }.returns(flow)
        }

        suspend fun givenInsertCallSucceeds() = apply {
            coEvery { callDAO.insertCall(any()) }.returns(Unit)
        }

        suspend fun givenGetCallerIdByConversationIdReturns(callerId: String?) = apply {
            coEvery {
                callDAO.getCallerIdByConversationId(any())
            }.returns(callerId)
        }

        suspend fun givenObserveConversationDetailsByIdReturns(flow: Flow<Either<StorageFailure, ConversationDetails>>) = apply {
            coEvery {
                conversationRepository.observeConversationDetailsById(any())
            }.returns(flow)
        }

        suspend fun givenGetKnownUserSucceeds() = apply {
            coEvery {
                userRepository.getKnownUser(any())
            }.returns(flowOf(TestUser.OTHER))
        }

        suspend fun givenGetKnownUserMinimizedSucceeds() = apply {
            coEvery {
                userRepository.getUsersMinimizedByQualifiedIDs(any())
            }.returns(listOf(TestUser.OTHER_MINIMIZED).right())
        }

        suspend fun givenGetTeamSucceeds() = apply {
            coEvery {
                teamRepository.getTeam(any())
            }.returns(flowOf(TestTeam.TEAM))
        }

        suspend fun givenGetCallStatusByConversationIdReturns(status: CallEntity.Status?) = apply {
            coEvery {
                callDAO.getCallStatusByConversationId(any())
            }.returns(status)
        }

        suspend fun givenPersistMessageSuccessful() = apply {
            coEvery {
                persistMessage.invoke(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun givenGetConversationProtocolInfoReturns(protocolInfo: Conversation.ProtocolInfo) = apply {
            coEvery {
                conversationRepository.getConversationProtocolInfo(any())
            }.returns(Either.Right(protocolInfo))
        }

        suspend fun givenJoinSubconversationSuccessful() = apply {
            coEvery {
                joinSubconversationUseCase.invoke(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun givenLeaveSubconversationSuccessful() = apply {
            coEvery {
                leaveSubconversationUseCase.invoke(any(), any())
            }.returns(Either.Right(Unit))
        }

        fun givenObserveEpochChangesReturns(flow: Flow<GroupID>) = apply {
            every {
                epochChangesObserver.observe()
            }.returns(flow)
        }

        suspend fun givenUpdateKeyMaterialSucceeds() = apply {
            coEvery {
                mlsConversationRepository.updateKeyingMaterial(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun givenRemoveClientsFromMLSGroupSucceeds() = apply {
            coEvery {
                mlsConversationRepository.removeClientsFromMLSGroup(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun givenGetSubconversationInfoReturns(groupId: GroupID?) = apply {
            coEvery {
                subconversationRepository.getSubconversationInfo(any(), any())
            }.returns(groupId)
        }

        suspend fun givenGetMLSClientSucceeds() = apply {
            coEvery {
                mlsClientProvider.getMLSClient(null)
            }.returns(Either.Right(mlsClient))
        }

        suspend fun givenGetMlsEpochReturns(epoch: ULong) = apply {
            coEvery {
                mlsClient.conversationEpoch(any())
            }.returns(epoch)
        }

        suspend fun givenMlsMembersReturns(members: List<CryptoQualifiedClientId>) = apply {
            coEvery {
                mlsClient.members(any())
            }.returns(members)
        }

        suspend fun givenDeriveSecretSuccessful() = apply {
            coEvery {
                mlsClient.deriveSecret(any(), any())
            }.returns(ByteArray(32))
        }

        companion object {
            const val CALL_CONFIG_API_RESPONSE = "{'call':'success','config':'dummy_config'}"
            val randomConversationId = ConversationId("value", "domain")

            val groupId = GroupID("groupid")
            val subconversationGroupId = GroupID("subconversation_groupid")
            val conversationId = ConversationId(value = "convId", domain = "domainId")
            val groupConversation = TestConversation.GROUP().copy(id = conversationId)
            val oneOnOneConversation = TestConversation.one_on_one(conversationId)
            val callerId = UserId(value = "callerId", domain = "domain")
            const val callerIdString = "callerId@domain"

            val oneOnOneConversationDetails = ConversationDetails.OneOne(
                conversation = oneOnOneConversation,
                otherUser = TestUser.OTHER,
                userType = UserType.INTERNAL,
            )

            val mlsProtocolInfo = Conversation.ProtocolInfo.MLS(
                groupId,
                Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                1UL,
                Clock.System.now(),
                CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            )

            val qualifiedClientID = QualifiedClientID(
                ClientId("abcd"),
                QualifiedID("participantId", "participantDomain")
            )
            val participant = ParticipantMinimized(
                id = qualifiedClientID.userId,
                userId = qualifiedClientID.userId,
                clientId = qualifiedClientID.clientId.value,
                isMuted = true,
                isCameraOn = false,
                isSharingScreen = false,
                hasEstablishedAudio = true
            )
        }
    }
}
