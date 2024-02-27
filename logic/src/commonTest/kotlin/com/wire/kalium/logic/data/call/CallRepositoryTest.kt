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
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.call.CallRepositoryTest.Arrangement.Companion.callerId
import com.wire.kalium.logic.data.call.mapper.CallMapperImpl
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
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
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.conversation.mls.EpochChangesObserver
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
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
import io.mockative.Times
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.fun1
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.oneOf
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
            .givenObserveCallsReturns(flowOf(listOf<CallEntity>()))
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
                            lastMessage = null,
                            isSelfUserMember = true,
                            isSelfUserCreator = true,
                            unreadEventCount = emptyMap(),
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
            callerId = Arrangement.callerId.value,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationType.Conference
        )

        // then
        verify(arrangement.callDAO).suspendFunction(arrangement.callDAO::insertCall)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun whenStartingAGroupCall_withExistingClosedCall_ThenSaveCallToDatabase() = runTest {
        val (arrangement, callRepository) = Arrangement()
            .givenObserveConversationDetailsByIdReturns(
                flowOf(
                    Either.Right(
                        ConversationDetails.Group(
                            Arrangement.groupConversation,
                            lastMessage = null,
                            isSelfUserMember = true,
                            isSelfUserCreator = true,
                            unreadEventCount = emptyMap(),
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
            callerId = Arrangement.callerId.value,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationType.Conference
        )

        // then
        verify(arrangement.callDAO).suspendFunction(arrangement.callDAO::insertCall)
            .with(any())
            .wasInvoked(exactly = once)

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
                            lastMessage = null,
                            isSelfUserMember = true,
                            isSelfUserCreator = true,
                            unreadEventCount = emptyMap(),
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
            callerId = callerId.value,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationType.Conference
        )

        // then
        verify(arrangement.callDAO).suspendFunction(arrangement.callDAO::insertCall)
            .with(any())
            .wasInvoked(exactly = once)

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
                            lastMessage = null,
                            isSelfUserMember = true,
                            isSelfUserCreator = true,
                            unreadEventCount = emptyMap(),
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
            callerId = callerId.value,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationType.Conference
        )

        // then
        verify(arrangement.callDAO).suspendFunction(arrangement.callDAO::insertCall)
            .with(any())
            .wasInvoked(exactly = Times(0))

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
                            lastMessage = null,
                            isSelfUserMember = true,
                            isSelfUserCreator = true,
                            unreadEventCount = emptyMap(),
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
            callerId = callerId.value,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationType.Conference
        )

        // then
        verify(arrangement.callDAO).suspendFunction(arrangement.callDAO::updateLastCallStatusByConversationId)
            .with(
                eq(CallEntity.Status.STILL_ONGOING),
                eq(callEntity.conversationId)
            )
            .wasInvoked(exactly = once)

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
            callerId = callerId.value,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationType.OneOnOne
        )

        // then
        verify(arrangement.callDAO).suspendFunction(arrangement.callDAO::insertCall)
            .with(any())
            .wasInvoked(exactly = once)
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
            callerId = callerId.value,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationType.OneOnOne
        )

        // then
        verify(arrangement.callDAO).suspendFunction(arrangement.callDAO::insertCall)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(any())
            .wasNotInvoked()

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
            callerId = callerId.value,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationType.OneOnOne
        )

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(any())
            .wasInvoked(exactly = Times(0))

        verify(arrangement.callDAO).suspendFunction(arrangement.callDAO::insertCall)
            .with(any())
            .wasInvoked(exactly = once)

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
            callerId = callerId.value,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationType.OneOnOne
        )

        // then
        verify(arrangement.callDAO).suspendFunction(arrangement.callDAO::insertCall)
            .with(any())
            .wasInvoked(exactly = Times(0))

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(any())
            .wasNotInvoked()

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
            callerId = callerId.value,
            isMuted = true,
            isCameraOn = false,
            isCbrEnabled = false,
            type = ConversationType.OneOnOne
        )

        // then
        verify(arrangement.callDAO).suspendFunction(arrangement.callDAO::updateLastCallStatusByConversationId)
            .with(eq(CallEntity.Status.CLOSED), eq(Arrangement.conversationId.toDao()))
            .wasInvoked(exactly = once)

        verify(arrangement.callDAO).suspendFunction(arrangement.callDAO::insertCall)
            .with(any())
            .wasInvoked(exactly = once)

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
        verify(arrangement.callDAO)
            .suspendFunction(arrangement.callDAO::updateLastCallStatusByConversationId)
            .with(
                eq(CallEntity.Status.ESTABLISHED),
                eq(callEntity.conversationId)
            )
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationIdThatDoesNotExistsInTheFlow_whenUpdateCallStatusIsCalled_thenUpdateTheStatus() = runTest {
        val (arrangement, callRepository) = Arrangement().arrange()

        callRepository.updateCallStatusById(Arrangement.randomConversationId, CallStatus.INCOMING)

        verify(arrangement.callDAO)
            .suspendFunction(arrangement.callDAO::updateLastCallStatusByConversationId)
            .with(any(), any())
            .wasInvoked(exactly = Times(1))
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
        val (_, callRepository) = Arrangement().arrange()
        val participantsList = listOf(
            Participant(
                id = QualifiedID("participantId", "participantDomain"),
                clientId = "abcd",
                name = "name",
                isMuted = true,
                isSpeaking = false,
                isCameraOn = false,
                isSharingScreen = false,
                avatarAssetId = null,
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
    fun givenAConversationIdThatDoesNotExistsInTheFlow_whenUpdateParticipantsActiveSpeakerIsCalled_thenDoNotUpdateTheFlow() = runTest {
        val (_, callRepository) = Arrangement().arrange()
        callRepository.updateParticipantsActiveSpeaker(
            Arrangement.randomConversationId,
            CallActiveSpeakers(emptyList())
        )

        assertFalse {
            callRepository.getCallMetadataProfile().data.containsKey(Arrangement.randomConversationId)
        }
    }

    @Test
    fun givenAConversationIdThatExistsInTheFlow_whenUpdateParticipantActiveSpeakerIsCalled_thenUpdateCallStatusInTheFlow() = runTest {
        // given
        val (_, callRepository) = Arrangement().arrange()
        val participant = Participant(
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
        val participantsList = listOf(participant)
        val expectedParticipantsList = listOf(participant.copy(isSpeaking = true))
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
        val activeSpeakers = CallActiveSpeakers(
            activeSpeakers = listOf(
                CallActiveSpeaker(
                    userId = "participantId@participantDomain",
                    clientId = "abcd",
                    audioLevel = 1,
                    audioLevelNow = 1
                )
            )
        )

        callRepository.updateCallParticipants(
            Arrangement.conversationId,
            participantsList
        )

        // when
        callRepository.updateParticipantsActiveSpeaker(Arrangement.conversationId, activeSpeakers)

        // then
        val metadata = callRepository.getCallMetadataProfile().data[Arrangement.conversationId]
        assertEquals(
            expectedParticipantsList,
            metadata?.participants
        )

        assertEquals(
            true,
            metadata?.participants?.get(0)?.isSpeaking
        )
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

        verify(arrangement.callDAO)
            .suspendFunction(arrangement.callDAO::getCallerIdByConversationId)
            .with(eq(Arrangement.conversationId.toDao()))
            .wasInvoked(exactly = once)

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAMissedCallAndNoCallerId_whenPersistMissedCallInvoked_thenDontStoreMissedCallInDatabase() = runTest {

        val (arrangement, callRepository) = Arrangement()
            .givenGetCallerIdByConversationIdReturns(null)
            .givenPersistMessageSuccessful()
            .arrange()

        callRepository.persistMissedCall(Arrangement.conversationId)

        verify(arrangement.callDAO)
            .suspendFunction(arrangement.callDAO::getCallerIdByConversationId)
            .with(eq(Arrangement.conversationId.toDao()))
            .wasInvoked(exactly = once)

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenMlsConferenceCall_whenJoinMlsConference_thenJoinSubconversation() = runTest {
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

        callRepository.joinMlsConference(Arrangement.conversationId) { _, _ -> }

        verify(arrangement.joinSubconversationUseCase)
            .suspendFunction(arrangement.joinSubconversationUseCase::invoke)
            .with(eq(Arrangement.conversationId), eq(CALL_SUBCONVERSATION_ID))
            .wasInvoked(exactly = once)
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
        callRepository.joinMlsConference(Arrangement.conversationId) { _, _ ->
            onEpochChangeCallCount += 1
        }
        yield()
        advanceUntilIdle()

        verify(arrangement.epochChangesObserver)
            .function(arrangement.epochChangesObserver::observe)
            .wasInvoked(exactly = once)

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
        callRepository.joinMlsConference(Arrangement.conversationId) { _, _ ->
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
        callRepository.joinMlsConference(Arrangement.conversationId) { _, _ ->
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

        callRepository.joinMlsConference(Arrangement.conversationId) { _, _ -> }
        yield()
        advanceUntilIdle()

        callRepository.leaveMlsConference(Arrangement.conversationId)
        yield()
        advanceUntilIdle()

        verify(arrangement.leaveSubconversationUseCase)
            .suspendFunction(arrangement.leaveSubconversationUseCase::invoke)
            .with(eq(Arrangement.conversationId), eq(CALL_SUBCONVERSATION_ID))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsConferenceCall_whenAdvanceEpoch_thenKeyMaterialIsUpdatedInSubconversation() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, callRepository) = Arrangement()
            .givenGetSubconversationInfoReturns(Arrangement.subconversationGroupId)
            .givenUpdateKeyMaterialSucceeds()
            .arrange()

        callRepository.advanceEpoch(Arrangement.conversationId)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::updateKeyingMaterial)
            .with(eq(Arrangement.subconversationGroupId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsConferenceCall_whenParticipantStaysUnconnected_thenParticipantGetRemovedFromSubconversation() = runTest(
        TestKaliumDispatcher.main
    ) {
        val (arrangement, callRepository) = Arrangement()
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
                Arrangement.participant.copy(
                hasEstablishedAudio = false
            )
            )
        )
        advanceTimeBy(CallDataSource.STALE_PARTICIPANT_TIMEOUT.toLong(DurationUnit.MILLISECONDS))
        yield()

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::removeClientsFromMLSGroup)
            .with(eq(Arrangement.subconversationGroupId), eq(listOf(Arrangement.qualifiedClientID)))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsConferenceCall_whenParticipantReconnects_thenParticipantIsNotRemovedFromSubconversation() = runTest(
        TestKaliumDispatcher.main
    ) {
        val (arrangement, callRepository) = Arrangement()
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

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::removeClientsFromMLSGroup)
            .with(eq(Arrangement.subconversationGroupId), eq(listOf(Arrangement.qualifiedClientID)))
            .wasNotInvoked()
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
        verify(arrangement.leaveSubconversationUseCase)
            .suspendFunction(arrangement.leaveSubconversationUseCase::invoke)
            .with(eq(Arrangement.conversationId), eq(CALL_SUBCONVERSATION_ID))
            .wasInvoked(exactly = once)

    }

    private fun provideCall(id: ConversationId, status: CallStatus) = Call(
        conversationId = id,
        status = status,
        callerId = "callerId@domain",
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
        isMuted = true,
        isCameraOn = false,
        isCbrEnabled = false,
        conversationName = null,
        conversationType = Conversation.Type.GROUP,
        callerName = null,
        callerTeamName = null,
        callStatus = CallStatus.ESTABLISHED,
        protocol = Conversation.ProtocolInfo.Proteus
    )

    private class Arrangement {

        @Mock
        val callApi = mock(classOf<CallApi>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val userRepository = mock(classOf<UserRepository>())

        @Mock
        val teamRepository = mock(classOf<TeamRepository>())

        @Mock
        val sessionRepository = mock(classOf<SessionRepository>())

        @Mock
        val qualifiedIdMapper = mock(classOf<QualifiedIdMapper>())

        @Mock
        val persistMessage = mock(classOf<PersistMessageUseCase>())

        @Mock
        val mlsClientProvider = mock(classOf<MLSClientProvider>())

        @Mock
        val mlsClient = mock(classOf<MLSClient>())

        @Mock
        val joinSubconversationUseCase = mock(classOf<JoinSubconversationUseCase>())

        @Mock
        val leaveSubconversationUseCase = mock(classOf<LeaveSubconversationUseCase>())

        @Mock
        val subconversationRepository = mock(classOf<SubconversationRepository>())

        @Mock
        val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

        @Mock
        val epochChangesObserver = mock(classOf<EpochChangesObserver>())

        @Mock
        val callDAO = configure(mock(classOf<CallDAO>())) {
            stubsUnitByDefault = true
        }

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
            given(qualifiedIdMapper).function(qualifiedIdMapper::fromStringToQualifiedID)
                .whenInvokedWith(eq("convId@domainId"))
                .thenReturn(QualifiedID("convId", "domainId"))

            given(qualifiedIdMapper).function(qualifiedIdMapper::fromStringToQualifiedID)
                .whenInvokedWith(eq("random@domain"))
                .thenReturn(QualifiedID("random", "domain"))

            given(qualifiedIdMapper).function(qualifiedIdMapper::fromStringToQualifiedID)
                .whenInvokedWith(eq("callerId@domain"))
                .thenReturn(QualifiedID("callerId", "domain"))

            given(qualifiedIdMapper).function(qualifiedIdMapper::fromStringToQualifiedID)
                .whenInvokedWith(eq("callerId"))
                .thenReturn(QualifiedID("callerId", ""))
        }

        fun arrange() = this to callRepository

        fun givenEstablishedCall(callEntity: CallEntity) = apply {
            given(callDAO)
                .function(callDAO::getEstablishedCall)
                .whenInvoked()
                .thenReturn(callEntity)
        }

        fun givenGetCallConfigResponse(response: NetworkResponse<String>) = apply {
            given(callApi)
                .suspendFunction(callApi::getCallConfig)
                .whenInvokedWith(oneOf(null))
                .thenReturn(response)
        }

        fun givenObserveCallsReturns(flow: Flow<List<CallEntity>>) = apply {
            given(callDAO)
                .suspendFunction(callDAO::observeCalls)
                .whenInvoked()
                .thenReturn(flow)
        }

        fun givenObserveIncomingCallsReturns(flow: Flow<List<CallEntity>>) = apply {
            given(callDAO)
                .suspendFunction(callDAO::observeIncomingCalls)
                .whenInvoked()
                .thenReturn(flow)
        }

        fun givenObserveOngoingCallsReturns(flow: Flow<List<CallEntity>>) = apply {
            given(callDAO)
                .suspendFunction(callDAO::observeOngoingCalls)
                .whenInvoked()
                .thenReturn(flow)
        }

        fun givenObserveEstablishedCallsReturns(flow: Flow<List<CallEntity>>) = apply {
            given(callDAO)
                .suspendFunction(callDAO::observeEstablishedCalls)
                .whenInvoked()
                .thenReturn(flow)
        }

        fun givenInsertCallSucceeds() = apply {
            given(callDAO)
                .suspendFunction(callDAO::insertCall)
                .whenInvokedWith(any())
        }

        fun givenGetCallerIdByConversationIdReturns(callerId: String?) = apply {
            given(callDAO)
                .suspendFunction(callDAO::getCallerIdByConversationId)
                .whenInvokedWith(any())
                .thenReturn(callerId)
        }

        fun givenObserveConversationDetailsByIdReturns(flow: Flow<Either<StorageFailure, ConversationDetails>>) = apply {
            given(conversationRepository).suspendFunction(conversationRepository::observeConversationDetailsById)
                .whenInvokedWith(any())
                .thenReturn(flow)
        }

        fun givenGetKnownUserSucceeds() = apply {
            given(userRepository).suspendFunction(userRepository::getKnownUser)
                .whenInvokedWith(any())
                .thenReturn(flowOf(TestUser.OTHER))
        }

        fun givenGetTeamSucceeds() = apply {
            given(teamRepository).suspendFunction(teamRepository::getTeam)
                .whenInvokedWith(any())
                .thenReturn(flowOf(TestTeam.TEAM))
        }

        fun givenGetCallStatusByConversationIdReturns(status: CallEntity.Status?) = apply {
            given(callDAO)
                .suspendFunction(callDAO::getCallStatusByConversationId)
                .whenInvokedWith(any())
                .thenReturn(status)
        }

        fun givenPersistMessageSuccessful() = apply {
            given(persistMessage).suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun givenGetConversationProtocolInfoReturns(protocolInfo: Conversation.ProtocolInfo) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationProtocolInfo)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(protocolInfo))
        }

        fun givenJoinSubconversationSuccessful() = apply {
            given(joinSubconversationUseCase)
                .suspendFunction(joinSubconversationUseCase::invoke)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun givenLeaveSubconversationSuccessful() = apply {
            given(leaveSubconversationUseCase)
                .suspendFunction(leaveSubconversationUseCase::invoke)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun givenObserveEpochChangesReturns(flow: Flow<GroupID>) = apply {
            given(epochChangesObserver)
                .function(epochChangesObserver::observe)
                .whenInvoked()
                .thenReturn(flow)
        }

        fun givenUpdateKeyMaterialSucceeds() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::updateKeyingMaterial)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun givenRemoveClientsFromMLSGroupSucceeds() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::removeClientsFromMLSGroup)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun givenGetSubconversationInfoReturns(groupId: GroupID?) = apply {
            given(subconversationRepository)
                .suspendFunction(subconversationRepository::getSubconversationInfo)
                .whenInvokedWith(any(), any())
                .thenReturn(groupId)
        }

        fun givenGetMLSClientSucceeds() = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getMLSClient, fun1<ClientId>())
                .whenInvokedWith(eq(null))
                .thenReturn(Either.Right(mlsClient))
        }

        fun givenGetMlsEpochReturns(epoch: ULong) = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::conversationEpoch)
                .whenInvokedWith(any())
                .thenReturn(epoch)
        }

        fun givenMlsMembersReturns(members: List<CryptoQualifiedClientId>) = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::members)
                .whenInvokedWith(any())
                .thenReturn(members)
        }

        fun givenDeriveSecretSuccessful() = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::deriveSecret)
                .whenInvokedWith(any(), any())
                .thenReturn(ByteArray(32))
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
                lastMessage = null,
                unreadEventCount = emptyMap()
            )

            val mlsProtocolInfo = Conversation.ProtocolInfo.MLS(
                groupId,
                Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                1UL,
                Clock.System.now(),
                Conversation.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            )

            val qualifiedClientID = QualifiedClientID(
                ClientId("abcd"),
                QualifiedID("participantId", "participantDomain")
            )
            val participant = Participant(
                id = qualifiedClientID.userId,
                clientId = qualifiedClientID.clientId.value,
                name = "name",
                isMuted = true,
                isSpeaking = false,
                isCameraOn = false,
                isSharingScreen = false,
                avatarAssetId = null,
                hasEstablishedAudio = true
            )
        }
    }
}
