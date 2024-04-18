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
package com.wire.kalium.logic.sync.receiver.handler.legalhold

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.client.FetchSelfClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.FetchUsersClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.SelfClientsResult
import com.wire.kalium.logic.feature.legalhold.LegalHoldState
import com.wire.kalium.logic.feature.legalhold.MembersHavingLegalHoldClientUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForUserUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.logic.sync.receiver.conversation.message.MessageUnpackResult
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.util.thenReturnSequentially
import com.wire.kalium.util.DateTimeUtil.minusMilliseconds
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LegalHoldHandlerTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatchers.default)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenLegalHoldEvent_whenUserIdIsSelfUserThenUpdateSelfUserClientsAndDeleteLegalHoldRequest() = runTest {
        val (arrangement, handler) = Arrangement()
            .withDeleteLegalHoldSuccess()
            .withSetLegalHoldChangeNotifiedSuccess()
            .withFetchSelfClientsFromRemoteSuccess()
            .arrange()

        handler.handleEnable(legalHoldEventEnabled)

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::deleteLegalHoldRequest)
            .wasInvoked(once)

        advanceUntilIdle()
        verify(arrangement.fetchSelfClientsFromRemote)
            .suspendFunction(arrangement.fetchSelfClientsFromRemote::invoke)
            .wasInvoked(once)
        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setLegalHoldChangeNotified)
            .with(eq(false))
            .wasInvoked(once)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenLegalHoldEvent_whenUserIdIsOtherUserThenUpdateOtherUserClients() = runTest {
        val (arrangement, handler) = Arrangement().arrange()

        handler.handleDisable(legalHoldEventDisabled)

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::deleteLegalHoldRequest)
            .wasNotInvoked()

        advanceUntilIdle()
        verify(arrangement.fetchUsersClientsFromRemote)
            .suspendFunction(arrangement.fetchUsersClientsFromRemote::invoke)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenUserLegalHoldDisabled_whenHandlingEnable_thenCreateOrUpdateSystemMessages() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Disabled)
            .withSetLegalHoldChangeNotifiedSuccess()
            .arrange()
        // when
        handler.handleEnable(legalHoldEventEnabled)
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForUser)
            .with(any())
            .wasInvoked()
    }

    @Test
    fun givenUserLegalHoldEnabled_whenHandlingEnable_thenDoNotCreateOrUpdateSystemMessages() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Enabled)
            .withSetLegalHoldChangeNotifiedSuccess()
            .arrange()
        // when
        handler.handleEnable(legalHoldEventEnabled)
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForUser)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenUserLegalHoldEnabled_whenHandlingDisable_thenCreateOrUpdateSystemMessages() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Enabled)
            .withSetLegalHoldChangeNotifiedSuccess()
            .arrange()
        // when
        handler.handleDisable(legalHoldEventDisabled)
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleDisabledForUser)
            .with(any())
            .wasInvoked()
    }

    @Test
    fun givenUserLegalHoldDisabled_whenHandlingDisable_thenDoNotCreateOrUpdateSystemMessages() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Disabled)
            .arrange()
        // when
        handler.handleDisable(legalHoldEventDisabled)
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleDisabledForUser)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenUserLegalHoldEnabled_whenHandlingEnable_thenDoNotSetLegalHoldChangeNotified() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Enabled)
            .withSetLegalHoldChangeNotifiedSuccess()
            .arrange()
        // when
        handler.handleEnable(legalHoldEventEnabled)
        // then
        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setLegalHoldChangeNotified)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenUserLegalHoldDisabled_whenHandlingEnableForSelf_thenSetLegalHoldChangeNotifiedAsFalse() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Disabled)
            .withSetLegalHoldChangeNotifiedSuccess()
            .arrange()
        // when
        handler.handleEnable(legalHoldEventEnabled.copy(userId = TestUser.SELF.id))
        // then
        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setLegalHoldChangeNotified)
            .with(eq(false))
            .wasInvoked()
    }

    @Test
    fun givenUserLegalHoldDisabled_whenHandlingEnableForOther_thenDoNotSetLegalHoldChangeNotified() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Disabled)
            .withSetLegalHoldChangeNotifiedSuccess()
            .arrange()
        // when
        handler.handleEnable(legalHoldEventEnabled.copy(userId = TestUser.OTHER_USER_ID))
        // then
        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setLegalHoldChangeNotified)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenUserLegalHoldDisabled_whenHandlingDisable_thenDoNotSetLegalHoldChangeNotified() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Disabled)
            .withSetLegalHoldChangeNotifiedSuccess()
            .arrange()
        // when
        handler.handleDisable(legalHoldEventDisabled)
        // then
        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setLegalHoldChangeNotified)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenUserLegalHoldEnabled_whenHandlingDisableForSelf_thenSetLegalHoldChangeNotifiedAsFalse() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Enabled)
            .withSetLegalHoldChangeNotifiedSuccess()
            .arrange()
        // when
        handler.handleDisable(legalHoldEventDisabled.copy(userId = TestUser.SELF.id))
        // then
        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setLegalHoldChangeNotified)
            .with(eq(false))
            .wasInvoked()
    }

    @Test
    fun givenUserLegalHoldEnabled_whenHandlingDisableForOther_thenDoNotSetLegalHoldChangeNotified() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Enabled)
            .withSetLegalHoldChangeNotifiedSuccess()
            .arrange()
        // when
        handler.handleDisable(legalHoldEventDisabled.copy(userId = TestUser.OTHER_USER_ID))
        // then
        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setLegalHoldChangeNotified)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenConversationWithNoMoreUsersUnderLegalHold_whenHandlingDisable_thenHandleDisabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Enabled)
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED)))
            .arrange()
        // when
        handler.handleDisable(legalHoldEventDisabled)
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleDisabledForConversation)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }
    @Test
    fun givenConversationWithStillUsersUnderLegalHold_whenHandlingDisable_thenDoNotHandleDisabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED)))
            .arrange()
        // when
        handler.handleDisable(legalHoldEventDisabled.copy(userId = TestUser.OTHER_USER_ID))
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleDisabledForConversation)
            .with(any(), any())
            .wasNotInvoked()
    }
    @Test
    fun givenConversationLegalHoldAlreadyDisabled_whenHandlingDisable_thenDoNotHandleDisabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED)))
            .arrange()
        // when
        handler.handleDisable(legalHoldEventDisabled.copy(userId = TestUser.OTHER_USER_ID))
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleDisabledForConversation)
            .with(any(), any())
            .wasNotInvoked()
    }
    @Test
    fun givenFirstUserUnderLegalHoldAppeared_whenHandlingEnable_thenHandleEnabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED)))
            .withMembersHavingLegalHoldClientSuccess(listOf(TestUser.OTHER_USER_ID))
            .arrange()
        // when
        handler.handleEnable(legalHoldEventEnabled.copy(userId = TestUser.OTHER_USER_ID))
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForConversation)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }
    @Test
    fun givenNextUsersUnderLegalHoldAppeared_whenHandlingEnable_thenDoNotHandleEnabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED)))
            .withUpdateLegalHoldStatusSuccess(false)
            .withMembersHavingLegalHoldClientSuccess(listOf(TestUser.OTHER_USER_ID))
            .arrange()
        // when
        handler.handleEnable(legalHoldEventEnabled.copy(userId = TestUser.OTHER_USER_ID_2))
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForConversation)
            .with(any(), any())
            .wasNotInvoked()
    }
    @Test
    fun givenConversationLegalHoldAlreadyEnabled_whenHandlingEnable_thenDoNotHandleEnabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED)))
            .arrange()
        // when
        handler.handleEnable(legalHoldEventEnabled.copy(userId = TestUser.OTHER_USER_ID))
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForConversation)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenConversationWithLegalHoldDisabled_whenNewMessageWithLegalHoldDisabled_thenDoNotHandleDisabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED)))
            .withUpdateLegalHoldStatusSuccess(false)
            .arrange()
        // when
        handler.handleNewMessage(applicationMessage(Conversation.LegalHoldStatus.DISABLED), false)
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleDisabledForConversation)
            .with(any(), any())
            .wasNotInvoked()
    }
    @Test
    fun givenConversationWithLegalHoldDisabled_whenNewMessageWithLegalHoldEnabled_thenHandleEnabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED)))
            .arrange()
        // when
        handler.handleNewMessage(applicationMessage(Conversation.LegalHoldStatus.ENABLED), false)
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForConversation)
            .with(eq(TestConversation.CONVERSATION.id), any())
            .wasInvoked()
    }
    @Test
    fun givenConversationWithLegalHoldEnabled_whenNewMessageWithLegalHoldEnabled_thenDoNotHandleDisabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED)))
            .withUpdateLegalHoldStatusSuccess(false)
            .arrange()
        // when
        handler.handleNewMessage(applicationMessage(Conversation.LegalHoldStatus.ENABLED), false)
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForConversation)
            .with(any(), any())
            .wasNotInvoked()
    }
    @Test
    fun givenConversationWithLegalHoldEnabled_whenNewMessageWithLegalHoldDisabled_thenHandleDisabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED)))
            .arrange()
        // when
        handler.handleNewMessage(applicationMessage(Conversation.LegalHoldStatus.DISABLED), false)
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleDisabledForConversation)
            .with(eq(TestConversation.CONVERSATION.id), any())
            .wasInvoked()
    }
    @Test
    fun givenConversation_whenHandlingNewMessageWithChangedLegalHold_thenUseTimestampOfMessageMinus1msToCreateSystemMessage() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED)))
            .arrange()
        val message = applicationMessage(Conversation.LegalHoldStatus.ENABLED)
        // when
        handler.handleNewMessage(message, false)
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForConversation)
            .with(eq(TestConversation.CONVERSATION.id), eq(minusMilliseconds(message.timestampIso, 1)))
            .wasInvoked()
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenNewMessageWithChangedLegalHoldStateAndSyncing_whenHandlingNewMessage_thenBufferAndHandleItWhenSyncStateIsLive() = runTest {
        // given
        val syncStatesFlow = MutableStateFlow<SyncState>(SyncState.GatheringPendingEvents)
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED)))
            .withGetConversationMembersSuccess(listOf(TestUser.OTHER_USER_ID))
            .withMembersHavingLegalHoldClientSuccess(emptyList()) // checked before legal hold state change so empty
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Enabled) // checked after legal hold state change, that's why enabled
            .withSetLegalHoldChangeNotifiedSuccess()
            .withSyncStates(syncStatesFlow)
            .arrange()
        advanceUntilIdle()
        // when
        handler.handleNewMessage(applicationMessage(Conversation.LegalHoldStatus.ENABLED), false)
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForUser)
            .with(any())
            .wasNotInvoked()
        syncStatesFlow.emit(SyncState.Live)
        advanceUntilIdle()
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForUser)
            .with(eq(TestUser.OTHER_USER_ID))
            .wasInvoked()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenNewMessageWithChangedLegalHoldStateAndSynced_whenHandlingNewMessage_thenHandleItRightAway() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED)))
            .withGetConversationMembersSuccess(listOf(TestUser.OTHER_USER_ID))
            .withMembersHavingLegalHoldClientSuccess(emptyList()) // checked before legal hold state change so empty
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Enabled) // checked after legal hold state change, that's why enabled
            .withSetLegalHoldChangeNotifiedSuccess()
            .withSyncStates(flowOf(SyncState.Live))
            .arrange()
        advanceUntilIdle()
        // when
        handler.handleNewMessage(applicationMessage(Conversation.LegalHoldStatus.ENABLED), true)
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForUser)
            .with(eq(TestUser.OTHER_USER_ID))
            .wasInvoked()
    }

    @Test
    fun givenHandleMessageSendFailureFails_whenHandlingMessageSendFailure_thenPropagateThisFailure() = runTest {
        // given
        val conversationId = TestConversation.CONVERSATION.id
        val failure = CoreFailure.Unknown(null)
        val timestampIso = "2022-03-30T15:36:00.000Z"
        val handleFailure: () -> Either<CoreFailure, Unit> = { Either.Left(failure) }
        val (arrangement, handler) = Arrangement()
            .arrange()
        // when
        val result = handler.handleMessageSendFailure(conversationId, timestampIso, handleFailure)
        // then
        result.shouldFail() {
            assertEquals(failure, it)
        }
    }

    @Test
    fun givenLegalHoldEnabledForConversation_whenHandlingMessageSendFailure_thenHandleItProperlyAndReturnTrue() = runTest {
        // given
        val conversationId = TestConversation.CONVERSATION.id
        val timestampIso = "2022-03-30T15:36:00.000Z"
        val handleFailure: () -> Either<CoreFailure, Unit> = { Either.Right(Unit) }
        val membersHavingLegalHoldClientBefore = emptyList<UserId>()
        val membersHavingLegalHoldClientAfter = listOf(TestUser.OTHER_USER_ID)
        val (arrangement, handler) = Arrangement()
            .withMembersHavingLegalHoldClientSuccess(membersHavingLegalHoldClientBefore, membersHavingLegalHoldClientAfter)
            .withUpdateLegalHoldStatusSuccess(true)
            .arrange()
        // when
        val result = handler.handleMessageSendFailure(conversationId, timestampIso, handleFailure)
        // then
        result.shouldSucceed() {
            assertEquals(true, it)
        }
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForConversation)
            .with(eq(conversationId), any())
            .wasInvoked()
    }

    @Test
    fun givenLegalHoldDisabledForConversation_whenHandlingMessageSendFailure_thenHandleItProperlyAndReturnFalse() = runTest {
        // given
        val conversationId = TestConversation.CONVERSATION.id
        val timestampIso = "2022-03-30T15:36:00.000Z"
        val handleFailure: () -> Either<CoreFailure, Unit> = { Either.Right(Unit) }
        val membersHavingLegalHoldClientBefore = listOf(TestUser.OTHER_USER_ID)
        val membersHavingLegalHoldClientAfter = emptyList<UserId>()
        val (arrangement, handler) = Arrangement()
            .withMembersHavingLegalHoldClientSuccess(membersHavingLegalHoldClientBefore, membersHavingLegalHoldClientAfter)
            .withUpdateLegalHoldStatusSuccess(true)
            .arrange()
        // when
        val result = handler.handleMessageSendFailure(conversationId, timestampIso, handleFailure)
        // then
        result.shouldSucceed() {
            assertEquals(false, it)
        }
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleDisabledForConversation)
            .with(eq(conversationId), any())
            .wasInvoked()
    }
    @Test
    fun givenLegalHoldChangedForConversation_whenHandlingMessageSendFailure_thenUseTimestampOfMessageMinus1msForSystemMessage() = runTest {
        // given
        val conversationId = TestConversation.CONVERSATION.id
        val timestampIso = "2022-03-30T15:36:00.000Z"
        val handleFailure: () -> Either<CoreFailure, Unit> = { Either.Right(Unit) }
        val membersHavingLegalHoldClientBefore = emptyList<UserId>()
        val membersHavingLegalHoldClientAfter = listOf(TestUser.OTHER_USER_ID)
        val (arrangement, handler) = Arrangement()
            .withMembersHavingLegalHoldClientSuccess(membersHavingLegalHoldClientBefore, membersHavingLegalHoldClientAfter)
            .withUpdateLegalHoldStatusSuccess(true)
            .arrange()
        // when
        val result = handler.handleMessageSendFailure(conversationId, timestampIso, handleFailure)
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForConversation)
            .with(eq(conversationId), eq(minusMilliseconds(timestampIso, 1)))
            .wasInvoked()
    }

    @Test
    fun givenLegalHoldNotChangedForConversation_whenHandlingMessageSendFailure_thenHandleItProperlyAndReturnFalse() = runTest {
        // given
        val conversationId = TestConversation.CONVERSATION.id
        val timestampIso = "2022-03-30T15:36:00.000Z"
        val handleFailure: () -> Either<CoreFailure, Unit> = { Either.Right(Unit) }
        val membersHavingLegalHoldClientBefore = listOf(TestUser.OTHER_USER_ID)
        val membersHavingLegalHoldClientAfter = listOf(TestUser.OTHER_USER_ID)
        val (arrangement, handler) = Arrangement()
            .withMembersHavingLegalHoldClientSuccess(membersHavingLegalHoldClientBefore, membersHavingLegalHoldClientAfter)
            .withUpdateLegalHoldStatusSuccess(false)
            .arrange()
        // when
        val result = handler.handleMessageSendFailure(conversationId, timestampIso, handleFailure)
        // then
        result.shouldSucceed() {
            assertEquals(false, it)
        }
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleDisabledForConversation)
            .with(eq(conversationId), any())
            .wasNotInvoked()
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForConversation)
            .with(eq(conversationId), any())
            .wasNotInvoked()
    }

    @Test
    fun givenLegalHoldChangedForMembers_whenHandlingMessageSendFailure_thenHandleItProperly() = runTest {
        // given
        val conversationId = TestConversation.CONVERSATION.id
        val timestampIso = "2022-03-30T15:36:00.000Z"
        val handleFailure: () -> Either<CoreFailure, Unit> = { Either.Right(Unit) }
        val membersHavingLegalHoldClientBefore = listOf(TestUser.OTHER_USER_ID)
        val membersHavingLegalHoldClientAfter = listOf(TestUser.OTHER_USER_ID_2)
        val (arrangement, handler) = Arrangement()
            .withMembersHavingLegalHoldClientSuccess(membersHavingLegalHoldClientBefore, membersHavingLegalHoldClientAfter)
            .withUpdateLegalHoldStatusSuccess()
            .arrange()
        // when
        val result = handler.handleMessageSendFailure(conversationId, timestampIso, handleFailure)
        // then
        result.shouldSucceed()
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleDisabledForUser)
            .with(eq(TestUser.OTHER_USER_ID))
            .wasInvoked()
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForUser)
            .with(eq(TestUser.OTHER_USER_ID_2))
            .wasInvoked()
    }

    private fun testHandlingConversationMembersChanged(
        thereAreMembersWithLegalHoldEnabledAfterChange: Boolean,
        legalHoldStatusForConversationChanged: Boolean,
        handleEnabledForConversationInvoked: Boolean,
        handleDisabledForConversationInvoked: Boolean,
    ) = runTest {
        // given
        val conversationId = TestConversation.CONVERSATION.id
        val userId = TestUser.OTHER_USER_ID
        val membersHavingLegalHoldClient = if(thereAreMembersWithLegalHoldEnabledAfterChange) listOf(userId) else emptyList()
        val (arrangement, handler) = Arrangement()
            .withMembersHavingLegalHoldClientSuccess(membersHavingLegalHoldClient)
            .withUpdateLegalHoldStatusSuccess(isChanged = legalHoldStatusForConversationChanged)
            .arrange()
        // when
        val result = handler.handleConversationMembersChanged(conversationId)
        // then
        result.shouldSucceed()
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForConversation)
            .with(eq(conversationId), any())
            .let { if(handleEnabledForConversationInvoked) it.wasInvoked() else it.wasNotInvoked() }
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleDisabledForConversation)
            .with(eq(conversationId), any())
            .let { if(handleDisabledForConversationInvoked) it.wasInvoked() else it.wasNotInvoked() }
    }

    @Test
    fun givenAtLeastOneMemberWithLHEnabled_AndLHForConversationChanged_whenHandlingMembersChanged_thenHandleEnabledForConversation() =
        testHandlingConversationMembersChanged(
            thereAreMembersWithLegalHoldEnabledAfterChange = true,
            legalHoldStatusForConversationChanged = true,
            handleEnabledForConversationInvoked = true,
            handleDisabledForConversationInvoked = false,
        )

    @Test
    fun givenNoMemberWithLHEnabled_AndLHForConversationChanged_whenHandlingMembersChanged_thenHandleDisabledForConversation() =
        testHandlingConversationMembersChanged(
            thereAreMembersWithLegalHoldEnabledAfterChange = false,
            legalHoldStatusForConversationChanged = true,
            handleEnabledForConversationInvoked = false,
            handleDisabledForConversationInvoked = true,
        )

    @Test
    fun givenAtLeastOneMemberWithLHEnabled_AndLHForConversationDidNotChange_whenHandlingMembersChanged_thenDoNotHandleForConversation() =
        testHandlingConversationMembersChanged(
            thereAreMembersWithLegalHoldEnabledAfterChange = true,
            legalHoldStatusForConversationChanged = false,
            handleEnabledForConversationInvoked = false,
            handleDisabledForConversationInvoked = false,
        )

    @Test
    fun givenNoMemberWithLHEnabled_AndLHForConversationDidNotChange_whenHandlingMembersChanged_thenDoNotHandleForConversation() =
        testHandlingConversationMembersChanged(
            thereAreMembersWithLegalHoldEnabledAfterChange = true,
            legalHoldStatusForConversationChanged = false,
            handleEnabledForConversationInvoked = false,
            handleDisabledForConversationInvoked = false,
        )

    private fun testHandlingNewConnection(
        userLegalHoldStatus: LegalHoldState,
        connectionStatus: ConnectionState,
        expectedConversationLegalHoldStatus: Conversation.LegalHoldStatus,
    ) = runTest {
        // given
        val newConnectionEvent = TestEvent.newConnection(status = connectionStatus)
        val (arrangement, handler) = Arrangement()
            .withObserveLegalHoldStateForUserSuccess(userLegalHoldStatus)
            .withUpdateLegalHoldStatusSuccess(isChanged = true)
            .arrange()
        // when
        val result = handler.handleNewConnection(newConnectionEvent)
        // then
        result.shouldSucceed()
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateLegalHoldStatus)
            .with(eq(newConnectionEvent.connection.qualifiedConversationId), eq(expectedConversationLegalHoldStatus))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNewConnectionMissingLegalHoldConsent_whenHandling_thenUpdateConversationLegalHoldStatusToDegraded() =
        testHandlingNewConnection(
            userLegalHoldStatus = LegalHoldState.Disabled,
            connectionStatus = ConnectionState.MISSING_LEGALHOLD_CONSENT,
            expectedConversationLegalHoldStatus = Conversation.LegalHoldStatus.DEGRADED,
        )
    @Test
    fun givenNewConnectionAcceptedAndUserUnderLegalHold_whenHandling_thenUpdateConversationLegalHoldStatusToEnabled() =
        testHandlingNewConnection(
            userLegalHoldStatus = LegalHoldState.Enabled,
            connectionStatus = ConnectionState.ACCEPTED,
            expectedConversationLegalHoldStatus = Conversation.LegalHoldStatus.ENABLED,
        )
    @Test
    fun givenNewConnectionAcceptedAndUserNotUnderLegalHold_whenHandling_thenUpdateConversationLegalHoldStatusToDisabled() =
        testHandlingNewConnection(
            userLegalHoldStatus = LegalHoldState.Disabled,
            connectionStatus = ConnectionState.ACCEPTED,
            expectedConversationLegalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
        )

    @Test
    fun givenUserHasNotBeenButNowIsUnderLegalHold_whenHandlingUserFetch_thenChangeConversationStatusesToEnabled() = runTest {
        // given
        val userId = TestUser.OTHER_USER_ID
        val conversation = conversation(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED)
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation))
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Disabled)  // used before legal hold state change
            .withMembersHavingLegalHoldClientSuccess(listOf(userId)) // used after legal hold state change
            .withUpdateLegalHoldStatusSuccess(isChanged = true)
            .arrange()
        // when
        handler.handleUserFetch(userId, true)
        // then
        verify(arrangement.fetchUsersClientsFromRemote)
            .suspendFunction(arrangement.fetchUsersClientsFromRemote::invoke)
            .with(eq(listOf(userId)))
            .wasInvoked()
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForUser)
            .with(eq(userId))
            .wasInvoked()
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleDisabledForUser)
            .with(eq(userId))
            .wasNotInvoked()
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateLegalHoldStatus)
            .with(eq(conversation.id), eq(Conversation.LegalHoldStatus.ENABLED))
            .wasInvoked()
    }

    @Test
    fun givenUserHasBeenButNowIsNotUnderLegalHold_whenHandlingUserFetch_thenChangeConversationStatusesToDisabled() = runTest {
        // given
        val userId = TestUser.OTHER_USER_ID
        val conversation = conversation(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED)
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation))
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Enabled)  // used before legal hold state change
            .withMembersHavingLegalHoldClientSuccess(listOf()) // used after legal hold state change
            .withUpdateLegalHoldStatusSuccess(isChanged = true)
            .arrange()
        // when
        handler.handleUserFetch(userId, false)
        // then
        verify(arrangement.fetchUsersClientsFromRemote)
            .suspendFunction(arrangement.fetchUsersClientsFromRemote::invoke)
            .with(eq(listOf(userId)))
            .wasInvoked()
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForUser)
            .with(eq(userId))
            .wasNotInvoked()
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleDisabledForUser)
            .with(eq(userId))
            .wasInvoked()
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateLegalHoldStatus)
            .with(eq(conversation.id), eq(Conversation.LegalHoldStatus.DISABLED))
            .wasInvoked()
    }

    @Test
    fun givenUserIsStillNotUnderLegalHold_whenHandlingUserFetch_thenDoNotChangeStatuses() = runTest {
        // given
        val userId = TestUser.OTHER_USER_ID
        val conversation = conversation(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED)
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation))
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Disabled)  // used before legal hold state change
            .withMembersHavingLegalHoldClientSuccess(listOf()) // used after legal hold state change
            .withUpdateLegalHoldStatusSuccess(isChanged = false)
            .arrange()
        // when
        handler.handleUserFetch(userId, false)
        // then
        verify(arrangement.fetchUsersClientsFromRemote)
            .suspendFunction(arrangement.fetchUsersClientsFromRemote::invoke)
            .with(eq(listOf(userId)))
            .wasNotInvoked()
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForUser)
            .with(eq(userId))
            .wasNotInvoked()
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleDisabledForUser)
            .with(eq(userId))
            .wasNotInvoked()
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateLegalHoldStatus)
            .with(eq(conversation.id), any())
            .wasNotInvoked()
    }

    @Test
    fun givenUserIsStillUnderLegalHold_whenHandlingUserFetch_thenDoNotChangeStatuses() = runTest {
        // given
        val userId = TestUser.OTHER_USER_ID
        val conversation = conversation(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED)
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation))
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Enabled)  // used before legal hold state change
            .withMembersHavingLegalHoldClientSuccess(listOf(userId)) // used after legal hold state change
            .withUpdateLegalHoldStatusSuccess(isChanged = false)
            .arrange()
        // when
        handler.handleUserFetch(userId, true)
        // then
        verify(arrangement.fetchUsersClientsFromRemote)
            .suspendFunction(arrangement.fetchUsersClientsFromRemote::invoke)
            .with(eq(listOf(userId)))
            .wasNotInvoked()
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForUser)
            .with(eq(userId))
            .wasNotInvoked()
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleDisabledForUser)
            .with(eq(userId))
            .wasNotInvoked()
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateLegalHoldStatus)
            .with(eq(conversation.id), any())
            .wasNotInvoked()
    }

    private class Arrangement {

        @Mock
        val fetchUsersClientsFromRemote = mock(FetchUsersClientsFromRemoteUseCase::class)

        @Mock
        val fetchSelfClientsFromRemote = mock(FetchSelfClientsFromRemoteUseCase::class)

        @Mock
        val observeLegalHoldStateForUser = mock(ObserveLegalHoldStateForUserUseCase::class)

        @Mock
        val membersHavingLegalHoldClient = mock(MembersHavingLegalHoldClientUseCase::class)

        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val legalHoldSystemMessagesHandler = configure(mock(LegalHoldSystemMessagesHandler::class)) { stubsUnitByDefault = true }

        @Mock
        val observeSyncState = mock(ObserveSyncStateUseCase::class)

        init {
            withObserveLegalHoldStateForUserSuccess(LegalHoldState.Disabled)
            withFetchSelfClientsFromRemoteSuccess()
            withDeleteLegalHoldRequestSuccess()
            withGetConversationsByUserIdSuccess(emptyList())
            withMembersHavingLegalHoldClientSuccess(emptyList())
            withUpdateLegalHoldStatusSuccess()
            withSyncStates(flowOf(SyncState.GatheringPendingEvents))
        }

        fun arrange() =
            this to LegalHoldHandlerImpl(
                selfUserId = TestUser.SELF.id,
                fetchUsersClientsFromRemote = fetchUsersClientsFromRemote,
                fetchSelfClientsFromRemote = fetchSelfClientsFromRemote,
                observeLegalHoldStateForUser = observeLegalHoldStateForUser,
                membersHavingLegalHoldClient = membersHavingLegalHoldClient,
                conversationRepository = conversationRepository,
                userConfigRepository = userConfigRepository,
                legalHoldSystemMessagesHandler = legalHoldSystemMessagesHandler,
                observeSyncState = observeSyncState,
                kaliumDispatcher = testDispatchers,
            )

        fun withDeleteLegalHoldSuccess() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::deleteLegalHoldRequest)
                .whenInvoked()
                .thenReturn(Either.Right(Unit))
        }

        fun withSetLegalHoldChangeNotifiedSuccess() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::setLegalHoldChangeNotified)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withFetchSelfClientsFromRemoteSuccess() = apply {
            given(fetchSelfClientsFromRemote)
                .suspendFunction(fetchSelfClientsFromRemote::invoke)
                .whenInvoked()
                .thenReturn(SelfClientsResult.Success(emptyList(), ClientId("client-id")))
        }

        fun withObserveLegalHoldStateForUserSuccess(state: LegalHoldState) = apply {
            given(observeLegalHoldStateForUser)
                .suspendFunction(observeLegalHoldStateForUser::invoke)
                .whenInvokedWith(any())
                .thenReturn(flowOf(state))
        }

        fun withDeleteLegalHoldRequestSuccess() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::deleteLegalHoldRequest)
                .whenInvoked()
                .thenReturn(Either.Right(Unit))
        }
        fun withMembersHavingLegalHoldClientSuccess(result: List<UserId>) = apply {
            given(membersHavingLegalHoldClient)
                .suspendFunction(membersHavingLegalHoldClient::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(result))
        }
        fun withMembersHavingLegalHoldClientSuccess(vararg result: List<UserId>) = apply {
            given(membersHavingLegalHoldClient)
                .suspendFunction(membersHavingLegalHoldClient::invoke)
                .whenInvokedWith(any())
                .thenReturnSequentially(*result.map { Either.Right(it) }.toTypedArray())
        }
        fun withUpdateLegalHoldStatusSuccess(isChanged: Boolean = true) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateLegalHoldStatus)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(isChanged))
        }
        fun withGetConversationsByUserIdSuccess(conversations: List<Conversation> = emptyList()) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationsByUserId)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(conversations))
        }
        fun withGetConversationMembersSuccess(members: List<UserId>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationMembers)
                .whenInvokedWith(anything())
                .then { Either.Right(members) }
        }
        fun withSyncStates(syncStates: Flow<SyncState>) = apply {
            given(observeSyncState)
                .function(observeSyncState::invoke)
                .whenInvoked()
                .thenReturn(syncStates)
        }
    }

    companion object {
        private val testDispatchers: KaliumDispatcher = TestKaliumDispatcher
        private val legalHoldEventEnabled = Event.User.LegalHoldEnabled(
            id = "id-1",
            userId = TestUser.SELF.id,
        )
        private val legalHoldEventDisabled = Event.User.LegalHoldDisabled(
            id = "id-2",
            userId = TestUser.OTHER_USER_ID
        )
        private fun conversation(legalHoldStatus: Conversation.LegalHoldStatus) =
            TestConversation.CONVERSATION.copy(legalHoldStatus = legalHoldStatus)
        private fun applicationMessage(legalHoldStatus: Conversation.LegalHoldStatus) = MessageUnpackResult.ApplicationMessage(
            conversationId = TestConversation.CONVERSATION.id,
            timestampIso = Instant.DISTANT_PAST.toIsoDateTimeString(),
            senderUserId = TestUser.SELF.id,
            senderClientId = ClientId("clientID"),
            content = ProtoContent.Readable(
                messageUid = "messageUID",
                messageContent = MessageContent.Text(value = "messageContent"),
                expectsReadConfirmation = false,
                legalHoldStatus = legalHoldStatus,
                expiresAfterMillis = null
            )
        )
    }
}
