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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.logic.sync.receiver.conversation.message.MessageUnpackResult
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentiallyReturns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.deleteLegalHoldRequest()

        }

        advanceUntilIdle()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.fetchSelfClientsFromRemote.invoke()

        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setLegalHoldChangeNotified(eq(false))

        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenLegalHoldEvent_whenUserIdIsOtherUserThenUpdateOtherUserClients() = runTest {
        val (arrangement, handler) = Arrangement().arrange()

        handler.handleDisable(legalHoldEventDisabled)

        verifySuspend(VerifyMode.not) {
            arrangement.userConfigRepository.deleteLegalHoldRequest()

        }

        advanceUntilIdle()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.fetchUsersClientsFromRemote.invoke(any())

        }
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
        verifySuspend {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(any(), any())

        }
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
        verifySuspend(VerifyMode.not) {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(any(), any())

        }
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
        verifySuspend {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForUser(any(), any())

        }
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
        verifySuspend(VerifyMode.not) {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForUser(any(), any())

        }
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
        verifySuspend(VerifyMode.not) {
            arrangement.userConfigRepository.setLegalHoldChangeNotified(any())

        }
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
        verifySuspend {
            arrangement.userConfigRepository.setLegalHoldChangeNotified(eq(false))

        }
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
        verifySuspend(VerifyMode.not) {
            arrangement.userConfigRepository.setLegalHoldChangeNotified(any())

        }
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
        verifySuspend(VerifyMode.not) {
            arrangement.userConfigRepository.setLegalHoldChangeNotified(any())

        }
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
        verifySuspend {
            arrangement.userConfigRepository.setLegalHoldChangeNotified(eq(false))

        }
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
        verifySuspend(VerifyMode.not) {
            arrangement.userConfigRepository.setLegalHoldChangeNotified(any())

        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForConversation(any(), any())

        }
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
        verifySuspend(VerifyMode.not) {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForConversation(any(), any())

        }
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
        verifySuspend(VerifyMode.not) {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForConversation(any(), any())

        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(any(), any())

        }
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
        verifySuspend(VerifyMode.not) {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(any(), any())

        }
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
        verifySuspend(VerifyMode.not) {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(any(), any())

        }
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
        verifySuspend(VerifyMode.not) {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForConversation(any(), any())

        }
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
        verifySuspend {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(eq(TestConversation.CONVERSATION.id), any())

        }
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
        verifySuspend(VerifyMode.not) {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(any(), any())

        }
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
        verifySuspend {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForConversation(eq(TestConversation.CONVERSATION.id), any())

        }
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
        verifySuspend {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(
                eq(TestConversation.CONVERSATION.id),
                eq(message.instant - 1.milliseconds)
            )

        }
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
        verifySuspend(VerifyMode.not) {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(any(), any())

        }
        syncStatesFlow.emit(SyncState.Live)
        advanceUntilIdle()
        verifySuspend {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(eq(TestUser.OTHER_USER_ID), any())

        }
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
        verifySuspend {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(eq(TestUser.OTHER_USER_ID), any())

        }
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
        verifySuspend {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(eq(conversationId), any())

        }
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
        verifySuspend {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForConversation(eq(conversationId), any())

        }
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
        verifySuspend {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(
                eq(conversationId),
                eq(dateTime - 1.milliseconds)
            )

        }
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
        verifySuspend(VerifyMode.not) {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForConversation(eq(conversationId), any())

        }
        verifySuspend(VerifyMode.not) {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(eq(conversationId), any())

        }
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
        verifySuspend {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForUser(eq(TestUser.OTHER_USER_ID), any())

        }
        verifySuspend {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(eq(TestUser.OTHER_USER_ID_2), any())

        }
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
        if (handleEnabledForConversationInvoked) {
            verifySuspend {
                arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(eq(conversationId), any())
            }
        } else {
            verifySuspend(VerifyMode.not) {
                arrangement.legalHoldSystemMessagesHandler.handleEnabledForConversation(eq(conversationId), any())
            }
        }
        if (handleDisabledForConversationInvoked) {
            verifySuspend {
                arrangement.legalHoldSystemMessagesHandler.handleDisabledForConversation(eq(conversationId), any())
            }
        } else {
            verifySuspend(VerifyMode.not) {
                arrangement.legalHoldSystemMessagesHandler.handleDisabledForConversation(eq(conversationId), any())
            }
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateLegalHoldStatus(eq(conversationId), eq(Conversation.LegalHoldStatus.UNKNOWN))

        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateLegalHoldStatus(
                eq(newConnectionEvent.connection.qualifiedConversationId),
                eq(expectedConversationLegalHoldStatus)
            )

        }
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
        verifySuspend {
            arrangement.fetchUsersClientsFromRemote.invoke(any())

        }
        verifySuspend {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(eq(userId), any())

        }
        verifySuspend(VerifyMode.not) {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForUser(eq(userId), any())

        }
        verifySuspend {
            arrangement.conversationRepository.updateLegalHoldStatus(
                eq(conversation.id),
                eq(Conversation.LegalHoldStatus.ENABLED)
            )

        }
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
        verifySuspend {
            arrangement.fetchUsersClientsFromRemote.invoke(eq(listOf(userId)))

        }
        verifySuspend(VerifyMode.not) {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(eq(userId), any())

        }
        verifySuspend {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForUser(eq(userId), any())

        }
        verifySuspend {
            arrangement.conversationRepository.updateLegalHoldStatus(
                eq(conversation.id),
                eq(Conversation.LegalHoldStatus.DISABLED)
            )

        }
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
        verifySuspend(VerifyMode.not) {
            arrangement.fetchUsersClientsFromRemote.invoke(eq(listOf(userId)))

        }
        verifySuspend(VerifyMode.not) {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(eq(userId), any())

        }
        verifySuspend(VerifyMode.not) {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForUser(eq(userId), any())

        }
        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.updateLegalHoldStatus(
                eq(conversation.id),
                any()
            )

        }
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
        verifySuspend(VerifyMode.not) {
            arrangement.fetchUsersClientsFromRemote.invoke(eq(listOf(userId)))

        }
        verifySuspend(VerifyMode.not) {
            arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(eq(userId), any())

        }
        verifySuspend(VerifyMode.not) {
            arrangement.legalHoldSystemMessagesHandler.handleDisabledForUser(eq(userId), any())

        }
        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.updateLegalHoldStatus(
                eq(conversation.id),
                any()
            )

        }
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
            verifySuspend(VerifyMode.not) {
                arrangement.conversationRepository.updateLegalHoldStatus(
                    eq(conversation.id),
                    eq(Conversation.LegalHoldStatus.ENABLED)
                )

            }
            verifySuspend {
                arrangement.legalHoldSystemMessagesHandler.handleEnabledForUser(any(), any())

            }
        }

    private class Arrangement :
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        UserConfigRepositoryArrangement by UserConfigRepositoryArrangementImpl() {
        val fetchUsersClientsFromRemote = mock<FetchUsersClientsFromRemoteUseCase>(mode = MockMode.autoUnit)
        val fetchSelfClientsFromRemote = mock<FetchSelfClientsFromRemoteUseCase>()
        val observeLegalHoldStateForUser = mock<ObserveLegalHoldStateForUserUseCase>()
        val membersHavingLegalHoldClient = mock<MembersHavingLegalHoldClientUseCase>()
        val legalHoldSystemMessagesHandler = mock<LegalHoldSystemMessagesHandler>(mode = MockMode.autoUnit)
        val observeSyncState = mock<ObserveSyncStateUseCase>()

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
            withDeleteLegalHoldRequestSuccess()
        }

        override suspend fun withSetLegalHoldChangeNotifiedSuccess() = apply {
            everySuspend {
                userConfigRepository.setLegalHoldChangeNotified(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withFetchSelfClientsFromRemoteSuccess() = apply {
            everySuspend {
                fetchSelfClientsFromRemote.invoke()
            }.returns(SelfClientsResult.Success(emptyList(), ClientId("client-id")))
        }

        suspend fun withObserveLegalHoldStateForUserSuccess(state: LegalHoldState) = apply {
            everySuspend {
                observeLegalHoldStateForUser.invoke(any())
            }.returns(flowOf(state))
        }

        suspend fun withMembersHavingLegalHoldClientSuccess(result: List<UserId>) = apply {
            everySuspend {
                membersHavingLegalHoldClient.invoke(any())
            }.returns(Either.Right(result))
        }

        suspend fun withMembersHavingLegalHoldClientSuccess(vararg result: List<UserId>) = apply {
            everySuspend {
                membersHavingLegalHoldClient.invoke(any())
            } sequentiallyReturns result.map { Either.Right(it) }
        }

        override suspend fun withUpdateLegalHoldStatusSuccess(isChanged: Boolean) = apply {
            everySuspend {
                conversationRepository.updateLegalHoldStatus(any(), any())
            }.returns(Either.Right(isChanged))
        }

        suspend fun withUpdateLegalHoldStatusSuccess() = withUpdateLegalHoldStatusSuccess(true)

        override suspend fun withObserveConversationLegalHoldStatus(status: Conversation.LegalHoldStatus) = apply {
            everySuspend {
                conversationRepository.observeLegalHoldStatus(any())
            }.returns(flowOf(Either.Right(status)))
        }

        suspend fun withGetConversationsByUserIdSuccess(conversations: List<Conversation> = emptyList()) = apply {
            withConversationsForUserIdReturning(Either.Right(conversations))
        }

        suspend fun withGetConversationMembersSuccess(members: List<UserId>) = apply {
            withGetConversationMembers(members)
        }

        fun withSyncStates(syncStates: Flow<SyncState>) = apply {
            every {
                observeSyncState.invoke()
            }.returns(syncStates)
        }

        override suspend fun withObserveIsUserMember(userId: UserId, isMember: Boolean) = apply {
            everySuspend {
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
