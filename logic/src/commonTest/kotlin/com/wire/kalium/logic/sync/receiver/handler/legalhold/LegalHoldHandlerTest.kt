/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.client.FetchSelfClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.PersistOtherUserClientsUseCase
import com.wire.kalium.logic.feature.client.SelfClientsResult
import com.wire.kalium.logic.feature.legalhold.LegalHoldState
import com.wire.kalium.logic.feature.legalhold.MembersHavingLegalHoldClientUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForUserUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.conversation.message.MessageUnpackResult
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test

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
        verify(arrangement.persistOtherUserClients)
            .suspendFunction(arrangement.persistOtherUserClients::invoke)
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
            .with(any())
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
            .with(any())
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
            .with(any())
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
            .with(any())
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
            .with(any())
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
            .with(any())
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
        handler.handleNewMessage(applicationMessage(Conversation.LegalHoldStatus.DISABLED))
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleDisabledForConversation)
            .with(any())
            .wasNotInvoked()
    }
    @Test
    fun givenConversationWithLegalHoldDisabled_whenNewMessageWithLegalHoldEnabled_thenHandleEnabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED)))
            .arrange()
        // when
        handler.handleNewMessage(applicationMessage(Conversation.LegalHoldStatus.ENABLED))
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForConversation)
            .with(eq(TestConversation.CONVERSATION.id))
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
        handler.handleNewMessage(applicationMessage(Conversation.LegalHoldStatus.ENABLED))
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnabledForConversation)
            .with(any())
            .wasNotInvoked()
    }
    @Test
    fun givenConversationWithLegalHoldEnabled_whenNewMessageWithLegalHoldDisabled_thenHandleDisabledForConversation() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(conversation(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED)))
            .arrange()
        // when
        handler.handleNewMessage(applicationMessage(Conversation.LegalHoldStatus.DISABLED))
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleDisabledForConversation)
            .with(eq(TestConversation.CONVERSATION.id))
            .wasInvoked()
    }


    private class Arrangement {

        @Mock
        val persistOtherUserClients = mock(PersistOtherUserClientsUseCase::class)

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

        init {
            withObserveLegalHoldStateForUserSuccess(LegalHoldState.Disabled)
            withFetchSelfClientsFromRemoteSuccess()
            withDeleteLegalHoldRequestSuccess()
            withGetConversationsByUserIdSuccess(emptyList())
            withMembersHavingLegalHoldClientSuccess(emptyList())
            withUpdateLegalHoldStatusSuccess()
        }

        fun arrange() =
            this to LegalHoldHandlerImpl(
                selfUserId = TestUser.SELF.id,
                persistOtherUserClients = persistOtherUserClients,
                fetchSelfClientsFromRemote = fetchSelfClientsFromRemote,
                observeLegalHoldStateForUser = observeLegalHoldStateForUser,
                membersHavingLegalHoldClient = membersHavingLegalHoldClient,
                conversationRepository = conversationRepository,
                userConfigRepository = userConfigRepository,
                legalHoldSystemMessagesHandler = legalHoldSystemMessagesHandler,
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
    }

    companion object {
        private val testDispatchers: KaliumDispatcher = TestKaliumDispatcher
        private val legalHoldEventEnabled = Event.User.LegalHoldEnabled(
            transient = false,
            live = false,
            id = "id-1",
            userId = TestUser.SELF.id,
        )
        private val legalHoldEventDisabled = Event.User.LegalHoldDisabled(
            transient = false,
            live = false,
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
