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
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

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

        coVerify {
            arrangement.userConfigRepository.deleteLegalHoldRequest()
        }.wasInvoked(once)

        advanceUntilIdle()
        coVerify {
            arrangement.fetchSelfClientsFromRemote.invoke()
        }.wasInvoked(once)
        coVerify {
            arrangement.userConfigRepository.setLegalHoldChangeNotified(eq(false))
        }.wasInvoked(once)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenLegalHoldEvent_whenUserIdIsOtherUserThenUpdateOtherUserClients() = runTest {
        val (arrangement, handler) = Arrangement().arrange()

        handler.handleDisable(legalHoldEventDisabled)

        coVerify {
            arrangement.userConfigRepository.deleteLegalHoldRequest()
        }.wasNotInvoked()

        advanceUntilIdle()
        coVerify {
            arrangement.fetchUsersClientsFromRemote.invoke(any())
        }.wasInvoked(once)
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
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(any(), any())
        }.wasInvoked()
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
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(any(), any())
        }.wasNotInvoked()
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
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForUser(any(), any())
        }.wasInvoked()
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
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForUser(any(), any())
        }.wasNotInvoked()
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
        coVerify {
            arrangement.userConfigRepository.setLegalHoldChangeNotified(any())
        }.wasNotInvoked()
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
        coVerify {
            arrangement.userConfigRepository.setLegalHoldChangeNotified(eq(false))
        }.wasInvoked()
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
        coVerify {
            arrangement.userConfigRepository.setLegalHoldChangeNotified(any())
        }.wasNotInvoked()
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
        coVerify {
            arrangement.userConfigRepository.setLegalHoldChangeNotified(any())
        }.wasNotInvoked()
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
        coVerify {
            arrangement.userConfigRepository.setLegalHoldChangeNotified(eq(false))
        }.wasInvoked()
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
        coVerify {
            arrangement.userConfigRepository.setLegalHoldChangeNotified(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenConversationWithNoMoreUsersUnderLegalHold_whenHandlingDisable_thenHandleDisabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Enabled)
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED)))
            .withObserveConversationLegalHoldStatus(Conversation.LegalHoldStatus.ENABLED)
            .arrange()
        // when
        handler.handleDisable(legalHoldEventDisabled)
        // then
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForConversation(any(), any())
        }.wasInvoked(exactly = once)
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
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForConversation(any(), any())
        }.wasNotInvoked()
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
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForConversation(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenFirstUserUnderLegalHoldAppeared_whenHandlingEnable_thenHandleEnabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED)))
            .withObserveConversationLegalHoldStatus(Conversation.LegalHoldStatus.DISABLED)
            .withMembersHavingLegalHoldClientSuccess(listOf(TestUser.OTHER_USER_ID))
            .arrange()
        // when
        handler.handleEnable(legalHoldEventEnabled.copy(userId = TestUser.OTHER_USER_ID))
        // then
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenNextUsersUnderLegalHoldAppeared_whenHandlingEnable_thenDoNotHandleEnabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED)))
            .withObserveConversationLegalHoldStatus(Conversation.LegalHoldStatus.ENABLED)
            .withUpdateLegalHoldStatusSuccess(false)
            .withMembersHavingLegalHoldClientSuccess(listOf(TestUser.OTHER_USER_ID))
            .arrange()
        // when
        handler.handleEnable(legalHoldEventEnabled.copy(userId = TestUser.OTHER_USER_ID_2))
        // then
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenConversationLegalHoldAlreadyEnabled_whenHandlingEnable_thenDoNotHandleEnabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED)))
            .withObserveConversationLegalHoldStatus(Conversation.LegalHoldStatus.ENABLED)
            .arrange()
        // when
        handler.handleEnable(legalHoldEventEnabled.copy(userId = TestUser.OTHER_USER_ID))
        // then
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenConversationWithLegalHoldDisabled_whenNewMessageWithLegalHoldDisabled_thenDoNotHandleDisabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED)))
            .withObserveConversationLegalHoldStatus(Conversation.LegalHoldStatus.DISABLED)
            .withUpdateLegalHoldStatusSuccess(false)
            .arrange()
        // when
        handler.handleNewMessage(applicationMessage(Conversation.LegalHoldStatus.DISABLED), false)
        // then
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForConversation(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenConversationWithLegalHoldDisabled_whenNewMessageWithLegalHoldEnabled_thenHandleEnabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED)))
            .withObserveConversationLegalHoldStatus(Conversation.LegalHoldStatus.DISABLED)
            .arrange()
        // when
        handler.handleNewMessage(applicationMessage(Conversation.LegalHoldStatus.ENABLED), false)
        // then
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(eq(TestConversation.CONVERSATION.id), any())
        }.wasInvoked()
    }

    @Test
    fun givenConversationWithLegalHoldEnabled_whenNewMessageWithLegalHoldEnabled_thenDoNotHandleDisabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED)))
            .withObserveConversationLegalHoldStatus(Conversation.LegalHoldStatus.DISABLED)
            .withUpdateLegalHoldStatusSuccess(false)
            .arrange()
        // when
        handler.handleNewMessage(applicationMessage(Conversation.LegalHoldStatus.ENABLED), false)
        // then
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenConversationWithLegalHoldEnabled_whenNewMessageWithLegalHoldDisabled_thenHandleDisabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED)))
            .withObserveConversationLegalHoldStatus(Conversation.LegalHoldStatus.ENABLED)
            .arrange()
        // when
        handler.handleNewMessage(applicationMessage(Conversation.LegalHoldStatus.DISABLED), false)
        // then
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForConversation(eq(TestConversation.CONVERSATION.id), any())
        }.wasInvoked()
    }

    @Test
    fun givenConversation_whenHandlingNewMessageWithChangedLegalHold_thenUseTimestampOfMessageMinus1msToCreateSystemMessage() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED)))
            .withObserveConversationLegalHoldStatus(Conversation.LegalHoldStatus.DISABLED)
            .arrange()
        val message = applicationMessage(Conversation.LegalHoldStatus.ENABLED)
        // when
        handler.handleNewMessage(message, false)
        // then
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(
                eq(TestConversation.CONVERSATION.id),
                eq(message.instant - 1.milliseconds)
            )
        }.wasInvoked()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenNewMessageWithChangedLegalHoldStateAndSyncing_whenHandlingNewMessage_thenBufferAndHandleItWhenSyncStateIsLive() = runTest {
        // given
        val syncStatesFlow = MutableStateFlow<SyncState>(SyncState.GatheringPendingEvents)
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED)))
            .withObserveConversationLegalHoldStatus(Conversation.LegalHoldStatus.DISABLED)
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
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(any(), any())
        }.wasNotInvoked()
        syncStatesFlow.emit(SyncState.Live)
        advanceUntilIdle()
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(eq(TestUser.OTHER_USER_ID), any())
        }.wasInvoked()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenNewMessageWithChangedLegalHoldStateAndSynced_whenHandlingNewMessage_thenHandleItRightAway() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED)))
            .withObserveConversationLegalHoldStatus(Conversation.LegalHoldStatus.DISABLED)
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
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(eq(TestUser.OTHER_USER_ID), any())
        }.wasInvoked()
    }

    @Test
    fun givenHandleMessageSendFailureFails_whenHandlingMessageSendFailure_thenPropagateThisFailure() = runTest {
        // given
        val conversationId = TestConversation.CONVERSATION.id
        val failure = CoreFailure.Unknown(null)
        val dateTime = Instant.UNIX_FIRST_DATE
        val handleFailure: () -> Either<CoreFailure, Unit> = { Either.Left(failure) }
        val (_, handler) = Arrangement()
            .arrange()
        // when
        val result = handler.handleMessageSendFailure(conversationId, dateTime, handleFailure)
        // then
        result.shouldFail {
            assertEquals(failure, it)
        }
    }

    @Test
    fun givenLegalHoldEnabledForConversation_whenHandlingMessageSendFailure_thenHandleItProperlyAndReturnTrue() = runTest {
        // given
        val conversationId = TestConversation.CONVERSATION.id
        val dateTime = Instant.UNIX_FIRST_DATE
        val handleFailure: () -> Either<CoreFailure, Unit> = { Either.Right(Unit) }
        val membersHavingLegalHoldClientBefore = emptyList<UserId>()
        val membersHavingLegalHoldClientAfter = listOf(TestUser.OTHER_USER_ID)
        val (arrangement, handler) = Arrangement()
            .withMembersHavingLegalHoldClientSuccess(membersHavingLegalHoldClientBefore, membersHavingLegalHoldClientAfter)
            .withObserveConversationLegalHoldStatus(Conversation.LegalHoldStatus.DISABLED)
            .withUpdateLegalHoldStatusSuccess(true)
            .arrange()
        // when
        val result = handler.handleMessageSendFailure(conversationId, dateTime, handleFailure)
        // then
        result.shouldSucceed {
            assertEquals(true, it)
        }
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(eq(conversationId), any())
        }.wasInvoked()
    }

    @Test
    fun givenLegalHoldDisabledForConversation_whenHandlingMessageSendFailure_thenHandleItProperlyAndReturnFalse() = runTest {
        // given
        val conversationId = TestConversation.CONVERSATION.id
        val dateTime = Instant.UNIX_FIRST_DATE
        val handleFailure: () -> Either<CoreFailure, Unit> = { Either.Right(Unit) }
        val membersHavingLegalHoldClientBefore = listOf(TestUser.OTHER_USER_ID)
        val membersHavingLegalHoldClientAfter = emptyList<UserId>()
        val (arrangement, handler) = Arrangement()
            .withMembersHavingLegalHoldClientSuccess(membersHavingLegalHoldClientBefore, membersHavingLegalHoldClientAfter)
            .withObserveConversationLegalHoldStatus(Conversation.LegalHoldStatus.ENABLED)
            .withUpdateLegalHoldStatusSuccess(true)
            .arrange()
        // when
        val result = handler.handleMessageSendFailure(conversationId, dateTime, handleFailure)
        // then
        result.shouldSucceed {
            assertEquals(false, it)
        }
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForConversation(eq(conversationId), any())
        }.wasInvoked()
    }

    @Test
    fun givenLegalHoldChangedForConversation_whenHandlingMessageSendFailure_thenUseTimestampOfMessageMinus1msForSystemMessage() = runTest {
        // given
        val conversationId = TestConversation.CONVERSATION.id
        val dateTime = Instant.UNIX_FIRST_DATE
        val handleFailure: () -> Either<CoreFailure, Unit> = { Either.Right(Unit) }
        val membersHavingLegalHoldClientBefore = emptyList<UserId>()
        val membersHavingLegalHoldClientAfter = listOf(TestUser.OTHER_USER_ID)
        val (arrangement, handler) = Arrangement()
            .withMembersHavingLegalHoldClientSuccess(membersHavingLegalHoldClientBefore, membersHavingLegalHoldClientAfter)
            .withObserveConversationLegalHoldStatus(Conversation.LegalHoldStatus.DISABLED)
            .withUpdateLegalHoldStatusSuccess(true)
            .arrange()
        // when
        handler.handleMessageSendFailure(conversationId, dateTime, handleFailure)
        // then
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(
                eq(conversationId),
                eq(dateTime - 1.milliseconds)
            )
        }.wasInvoked()
    }

    @Test
    fun givenLegalHoldNotChangedForConversation_whenHandlingMessageSendFailure_thenHandleItProperlyAndReturnFalse() = runTest {
        // given
        val conversationId = TestConversation.CONVERSATION.id
        val dateTime = Instant.UNIX_FIRST_DATE
        val handleFailure: () -> Either<CoreFailure, Unit> = { Either.Right(Unit) }
        val membersHavingLegalHoldClientBefore = listOf(TestUser.OTHER_USER_ID)
        val membersHavingLegalHoldClientAfter = listOf(TestUser.OTHER_USER_ID)
        val (arrangement, handler) = Arrangement()
            .withMembersHavingLegalHoldClientSuccess(membersHavingLegalHoldClientBefore, membersHavingLegalHoldClientAfter)
            .withObserveConversationLegalHoldStatus(Conversation.LegalHoldStatus.DISABLED)
            .withUpdateLegalHoldStatusSuccess(false)
            .arrange()
        // when
        val result = handler.handleMessageSendFailure(conversationId, dateTime, handleFailure)
        // then
        result.shouldSucceed {
            assertEquals(false, it)
        }
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForConversation(eq(conversationId), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(eq(conversationId), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenLegalHoldChangedForMembers_whenHandlingMessageSendFailure_thenHandleItProperly() = runTest {
        // given
        val conversationId = TestConversation.CONVERSATION.id
        val dateTime = Instant.UNIX_FIRST_DATE
        val handleFailure: () -> Either<CoreFailure, Unit> = { Either.Right(Unit) }
        val membersHavingLegalHoldClientBefore = listOf(TestUser.OTHER_USER_ID)
        val membersHavingLegalHoldClientAfter = listOf(TestUser.OTHER_USER_ID_2)
        val (arrangement, handler) = Arrangement()
            .withMembersHavingLegalHoldClientSuccess(membersHavingLegalHoldClientBefore, membersHavingLegalHoldClientAfter)
            .withObserveConversationLegalHoldStatus(Conversation.LegalHoldStatus.ENABLED)
            .withUpdateLegalHoldStatusSuccess()
            .arrange()
        // when
        val result = handler.handleMessageSendFailure(conversationId, dateTime, handleFailure)
        // then
        result.shouldSucceed()
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForUser(eq(TestUser.OTHER_USER_ID), any())
        }.wasInvoked()
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(eq(TestUser.OTHER_USER_ID_2), any())
        }.wasInvoked()
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
        val membersHavingLegalHoldClient = if (thereAreMembersWithLegalHoldEnabledAfterChange) listOf(userId) else emptyList()
        val conversationLegalHoldStatusBefore = when {
            thereAreMembersWithLegalHoldEnabledAfterChange != legalHoldStatusForConversationChanged -> Conversation.LegalHoldStatus.ENABLED
            else -> Conversation.LegalHoldStatus.DISABLED
        }
        val (arrangement, handler) = Arrangement()
            .withMembersHavingLegalHoldClientSuccess(membersHavingLegalHoldClient)
            .withObserveConversationLegalHoldStatus(conversationLegalHoldStatusBefore)
            .withUpdateLegalHoldStatusSuccess(isChanged = legalHoldStatusForConversationChanged)
            .withObserveIsUserMember(TestUser.SELF.id, true)
            .arrange()
        // when
        val result = handler.handleConversationMembersChanged(conversationId)
        // then
        result.shouldSucceed()
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(eq(conversationId), any())
        }
            .let { if (handleEnabledForConversationInvoked) it.wasInvoked() else it.wasNotInvoked() }
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForConversation(eq(conversationId), any())
        }
            .let { if (handleDisabledForConversationInvoked) it.wasInvoked() else it.wasNotInvoked() }
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

    @Test
    fun givenSelfUserIsRemovedFromGroup_whenHandlingMembersChanged_thenResetConversationLegalHoldState() = runTest {
        // given
        val conversationId = TestConversation.CONVERSATION.id
        val selfUserId = TestUser.SELF.id
        val otherUserId = TestUser.OTHER_USER_ID
        val membersHavingLegalHoldClient = listOf(otherUserId)
        val conversationLegalHoldStatusBefore = Conversation.LegalHoldStatus.ENABLED
        val (arrangement, handler) = Arrangement()
            .withMembersHavingLegalHoldClientSuccess(membersHavingLegalHoldClient)
            .withObserveConversationLegalHoldStatus(conversationLegalHoldStatusBefore)
            .withUpdateLegalHoldStatusSuccess(isChanged = false)
            .withObserveIsUserMember(selfUserId, false)
            .arrange()
        // when
        val result = handler.handleConversationMembersChanged(conversationId)
        // then
        result.shouldSucceed()
        coVerify {
            arrangement.conversationRepository.updateLegalHoldStatus(eq(conversationId), eq(Conversation.LegalHoldStatus.UNKNOWN))
        }.wasInvoked(exactly = 1)
    }

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
        coVerify {
            arrangement.conversationRepository.updateLegalHoldStatus(
                eq(newConnectionEvent.connection.qualifiedConversationId),
                eq(expectedConversationLegalHoldStatus)
            )
        }.wasInvoked(exactly = once)
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
            .withObserveConversationLegalHoldStatus(Conversation.LegalHoldStatus.DISABLED) // used before legal hold state change
            .withMembersHavingLegalHoldClientSuccess(listOf(userId)) // used after legal hold state change
            .withUpdateLegalHoldStatusSuccess(isChanged = true)
            .arrange()
        // when
        handler.handleUserFetch(userId, true)
        // then
        coVerify {
            arrangement.fetchUsersClientsFromRemote.invoke(any())
        }.wasInvoked()
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(eq(userId), any())
        }.wasInvoked()
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForUser(eq(userId), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.conversationRepository.updateLegalHoldStatus(
                eq(conversation.id),
                eq(Conversation.LegalHoldStatus.ENABLED)
            )
        }.wasInvoked()
    }

    @Test
    fun givenUserHasBeenButNowIsNotUnderLegalHold_whenHandlingUserFetch_thenChangeConversationStatusesToDisabled() = runTest {
        // given
        val userId = TestUser.OTHER_USER_ID
        val conversation = conversation(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED)
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation))
            .withObserveConversationLegalHoldStatus(Conversation.LegalHoldStatus.ENABLED)
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Enabled)  // used before legal hold state change
            .withMembersHavingLegalHoldClientSuccess(listOf()) // used after legal hold state change
            .withUpdateLegalHoldStatusSuccess(isChanged = true)
            .arrange()
        // when
        handler.handleUserFetch(userId, false)
        // then
        coVerify {
            arrangement.fetchUsersClientsFromRemote.invoke(eq(listOf(userId)))
        }.wasInvoked()
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(eq(userId), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForUser(eq(userId), any())
        }.wasInvoked()
        coVerify {
            arrangement.conversationRepository.updateLegalHoldStatus(
                eq(conversation.id),
                eq(Conversation.LegalHoldStatus.DISABLED)
            )
        }.wasInvoked()
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
        coVerify {
            arrangement.fetchUsersClientsFromRemote.invoke(eq(listOf(userId)))
        }.wasNotInvoked()
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(eq(userId), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForUser(eq(userId), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.conversationRepository.updateLegalHoldStatus(
                eq(conversation.id),
                any()
            )
        }.wasNotInvoked()
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
        coVerify {
            arrangement.fetchUsersClientsFromRemote.invoke(eq(listOf(userId)))
        }.wasNotInvoked()
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(eq(userId), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForUser(eq(userId), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.conversationRepository.updateLegalHoldStatus(
                eq(conversation.id),
                any()
            )
        }.wasNotInvoked()
    }

    @Test
    fun givenUserLegalHoldDisabledButConversationDegraded_whenHandlingEnable_thenDoNotChangeConversationStatusButCreateSystemMessages() =
        runTest {
            // given
            val conversation = conversation(legalHoldStatus = Conversation.LegalHoldStatus.DEGRADED)
            val (arrangement, handler) = Arrangement()
                .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Disabled)
                .withGetConversationsByUserIdSuccess(listOf(conversation))
                .withObserveConversationLegalHoldStatus(Conversation.LegalHoldStatus.DEGRADED)
                .withSetLegalHoldChangeNotifiedSuccess()
                .arrange()
            // when
            handler.handleEnable(legalHoldEventEnabled)
            // then
            coVerify {
                arrangement.conversationRepository.updateLegalHoldStatus(
                    eq(conversation.id),
                    eq(Conversation.LegalHoldStatus.ENABLED)
                )
            }.wasNotInvoked()
            coVerify {
                arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(any(), any())
            }.wasInvoked()
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
        val legalHoldSystemMessagesHandler = mock(LegalHoldSystemMessagesHandler::class)

        @Mock
        val observeSyncState = mock(ObserveSyncStateUseCase::class)

        init {
            runBlocking {
                withObserveLegalHoldStateForUserSuccess(LegalHoldState.Disabled)
                withFetchSelfClientsFromRemoteSuccess()
                withDeleteLegalHoldRequestSuccess()
                withGetConversationsByUserIdSuccess(emptyList())
                withMembersHavingLegalHoldClientSuccess(emptyList())
                withUpdateLegalHoldStatusSuccess()
                withSyncStates(flowOf(SyncState.GatheringPendingEvents))
            }
        }

        fun arrange() = run {
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
        }

        suspend fun withDeleteLegalHoldSuccess() = apply {
            coEvery {
                userConfigRepository.deleteLegalHoldRequest()
            }.returns(Either.Right(Unit))
        }

        suspend fun withSetLegalHoldChangeNotifiedSuccess() = apply {
            coEvery {
                userConfigRepository.setLegalHoldChangeNotified(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withFetchSelfClientsFromRemoteSuccess() = apply {
            coEvery {
                fetchSelfClientsFromRemote.invoke()
            }.returns(SelfClientsResult.Success(emptyList(), ClientId("client-id")))
        }

        suspend fun withObserveLegalHoldStateForUserSuccess(state: LegalHoldState) = apply {
            coEvery {
                observeLegalHoldStateForUser.invoke(any())
            }.returns(flowOf(state))
        }

        suspend fun withDeleteLegalHoldRequestSuccess() = apply {
            coEvery {
                userConfigRepository.deleteLegalHoldRequest()
            }.returns(Either.Right(Unit))
        }

        suspend fun withMembersHavingLegalHoldClientSuccess(result: List<UserId>) = apply {
            coEvery {
                membersHavingLegalHoldClient.invoke(any())
            }.returns(Either.Right(result))
        }

        suspend fun withMembersHavingLegalHoldClientSuccess(vararg result: List<UserId>) = apply {
            coEvery {
                membersHavingLegalHoldClient.invoke(any())
            }.thenReturnSequentially(*result.map { Either.Right(it) }.toTypedArray())
        }

        suspend fun withUpdateLegalHoldStatusSuccess(isChanged: Boolean = true) = apply {
            coEvery {
                conversationRepository.updateLegalHoldStatus(any(), any())
            }.returns(Either.Right(isChanged))
        }

        suspend fun withObserveConversationLegalHoldStatus(status: Conversation.LegalHoldStatus) = apply {
            coEvery {
                conversationRepository.observeLegalHoldStatus(any())
            }.returns(flowOf(Either.Right(status)))
        }

        suspend fun withGetConversationsByUserIdSuccess(conversations: List<Conversation> = emptyList()) = apply {
            coEvery {
                conversationRepository.getConversationsByUserId(any())
            }.returns(Either.Right(conversations))
        }

        suspend fun withGetConversationMembersSuccess(members: List<UserId>) = apply {
            coEvery {
                conversationRepository.getConversationMembers(any())
            }.returns(Either.Right(members))
        }

        fun withSyncStates(syncStates: Flow<SyncState>) = apply {
            every {
                observeSyncState.invoke()
            }.returns(syncStates)
        }

        suspend fun withObserveIsUserMember(userId: UserId, isMember: Boolean) = apply {
            coEvery {
                conversationRepository.observeIsUserMember(any(), eq(userId))
            }.returns(flowOf(Either.Right(isMember)))
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
            instant = Instant.DISTANT_PAST,
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
