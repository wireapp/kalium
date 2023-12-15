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
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.legalhold.MembersHavingLegalHoldClientUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.BeforeTest
import kotlin.test.Test

class LegalHoldSystemMessagesHandlerTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeTest
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
    }
    @Test
    fun givenNoLastLegalHoldEnabledMessageForConversation_whenHandlingEnable_thenCreateNewSystemMessage() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(TestConversation.CONVERSATION))
            .withGetLastMessagesForConversationIdsSuccess(mapOf(TestConversation.CONVERSATION.id to TestMessage.TEXT_MESSAGE))
            .arrange()
        // when
        handler.handleEnable(userId = TestUser.OTHER_USER_ID)
        // then
        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching {
                it.content is MessageContent.LegalHold.ForMembers.Enabled
                        && (it.content as MessageContent.LegalHold.ForMembers.Enabled).members == listOf(TestUser.OTHER_USER_ID)
            })
            .wasInvoked(exactly = once)
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::updateLegalHoldMessageMembers)
            .with(any())
            .wasNotInvoked()
    }
    @Test
    fun givenLastLegalHoldEnabledMessageForConversation_whenHandlingEnable_thenUpdateExistingSystemMessage() = runTest {
        // given
        val legalHoldMessage = testLegalHoldSystemMessage(MessageContent.LegalHold.ForMembers.Enabled(listOf(TestUser.OTHER_USER_ID_2)))
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(TestConversation.CONVERSATION))
            .withGetLastMessagesForConversationIdsSuccess(mapOf(TestConversation.CONVERSATION.id to legalHoldMessage))
            .arrange()
        // when
        handler.handleEnable(userId = TestUser.OTHER_USER_ID)
        // then
        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(any())
            .wasNotInvoked()
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::updateLegalHoldMessageMembers)
            .with(any(), any(), matching { it == listOf(TestUser.OTHER_USER_ID_2, TestUser.OTHER_USER_ID) })
            .wasInvoked(exactly = once)
    }
    @Test
    fun givenConversationLegalHoldStateIsDisabled_whenHandlingEnable_thenUpdateState() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(
                listOf(TestConversation.CONVERSATION.copy(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED))
            )
            .withMembersHavingLegalHoldClientSuccess(listOf(TestUser.USER_ID))
            .arrange()
        // when
        handler.handleEnable(userId = TestUser.OTHER_USER_ID)
        // then
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateLegalHoldStatus)
            .with(eq(TestConversation.CONVERSATION.id), eq(Conversation.LegalHoldStatus.ENABLED))
            .wasInvoked(exactly = once)
    }
    @Test
    fun givenConversationLegalHoldStateIsEnabled_whenHandlingEnable_thenDoNotUpdateState() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(
                listOf(TestConversation.CONVERSATION.copy(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED))
            )
            .withMembersHavingLegalHoldClientSuccess(listOf(TestUser.USER_ID))
            .arrange()
        // when
        handler.handleEnable(userId = TestUser.OTHER_USER_ID)
        // then
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateLegalHoldStatus)
            .with(any(), any())
            .wasNotInvoked()
    }
    @Test
    fun givenNoLastLegalHoldDisabledMessageForConversation_whenHandlingDisable_thenCreateNewSystemMessage() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(TestConversation.CONVERSATION))
            .withGetLastMessagesForConversationIdsSuccess(mapOf(TestConversation.CONVERSATION.id to TestMessage.TEXT_MESSAGE))
            .arrange()
        // when
        handler.handleDisable(userId = TestUser.OTHER_USER_ID)
        // then
        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching {
                it.content is MessageContent.LegalHold.ForMembers.Disabled
                        && (it.content as MessageContent.LegalHold.ForMembers.Disabled).members == listOf(TestUser.OTHER_USER_ID)
            })
            .wasInvoked(exactly = once)
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::updateLegalHoldMessageMembers)
            .with(any())
            .wasNotInvoked()
    }
    @Test
    fun givenLastLegalHoldDisabledMessageForConversation_whenHandlingDisable_thenUpdateExistingSystemMessage() = runTest {
        // given
        val legalHoldMessage = testLegalHoldSystemMessage(MessageContent.LegalHold.ForMembers.Disabled(listOf(TestUser.OTHER_USER_ID_2)))
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(TestConversation.CONVERSATION))
            .withGetLastMessagesForConversationIdsSuccess(mapOf(TestConversation.CONVERSATION.id to legalHoldMessage))
            .arrange()
        // when
        handler.handleDisable(userId = TestUser.OTHER_USER_ID)
        // then
        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(any())
            .wasNotInvoked()
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::updateLegalHoldMessageMembers)
            .with(any(), any(), matching { it == listOf(TestUser.OTHER_USER_ID_2, TestUser.OTHER_USER_ID) })
            .wasInvoked(exactly = once)
    }
    @Test
    fun givenConversationLegalHoldStateIsEnabled_whenHandlingDisable_thenUpdateState() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(
                listOf(TestConversation.CONVERSATION.copy(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED))
            )
            .arrange()
        // when
        handler.handleDisable(userId = TestUser.OTHER_USER_ID)
        // then
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateLegalHoldStatus)
            .with(eq(TestConversation.CONVERSATION.id), eq(Conversation.LegalHoldStatus.DISABLED))
            .wasInvoked(exactly = once)
    }
    @Test
    fun givenConversationLegalHoldStateIsDisabled_whenHandlingDisable_thenDoNotUpdateState() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(
                listOf(TestConversation.CONVERSATION.copy(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED))
            )
            .arrange()
        // when
        handler.handleDisable(userId = TestUser.OTHER_USER_ID)
        // then
        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateLegalHoldStatus)
            .with(any(), any())
            .wasNotInvoked()
    }
    @Test
    fun givenNoMoreUsersUnderLegalHold_whenHandlingDisable_thenCreateLegalHoldForConversationDisabledMessage() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(
                listOf(TestConversation.CONVERSATION.copy(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED))
            )
            .arrange()
        // when
        handler.handleDisable(userId = TestUser.OTHER_USER_ID)
        // then
        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching { it.content is MessageContent.LegalHold.ForConversation.Disabled })
            .wasInvoked(exactly = once)
    }
    @Test
    fun givenStillAreUsersUnderLegalHold_whenHandlingDisable_thenDoNotCreateLegalHoldForConversationDisabledMessage() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(
                listOf(TestConversation.CONVERSATION.copy(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED))
            )
            .withMembersHavingLegalHoldClientSuccess(listOf(TestUser.USER_ID))
            .arrange()
        // when
        handler.handleDisable(userId = TestUser.OTHER_USER_ID)
        // then
        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching { it.content is MessageContent.LegalHold.ForConversation.Disabled })
            .wasNotInvoked()
    }
    @Test
    fun givenConversationLegalHoldAlreadyDisabled_whenHandlingDisable_thenDoNotCreateLegalHoldForConversationDisabledMessage() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(
                listOf(TestConversation.CONVERSATION.copy(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED))
            )
            .arrange()
        // when
        handler.handleDisable(userId = TestUser.OTHER_USER_ID)
        // then
        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching { it.content is MessageContent.LegalHold.ForConversation.Disabled })
            .wasNotInvoked()
    }
    @Test
    fun givenFirstUserUnderLegalHoldAppeared_whenHandlingEnable_thenCreateLegalHoldForConversationEnabledMessage() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(
                listOf(TestConversation.CONVERSATION.copy(legalHoldStatus = Conversation.LegalHoldStatus.DISABLED))
            )
            .withMembersHavingLegalHoldClientSuccess(listOf(TestUser.OTHER_USER_ID))
            .arrange()
        // when
        handler.handleEnable(userId = TestUser.OTHER_USER_ID)
        // then
        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching { it.content is MessageContent.LegalHold.ForConversation.Enabled })
            .wasInvoked(exactly = once)
    }
    @Test
    fun givenNextUsersUnderLegalHoldAppeared_whenHandlingEnable_thenDoNotCreateLegalHoldForConversationEnabledMessage() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(
                listOf(TestConversation.CONVERSATION.copy(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED))
            )
            .withMembersHavingLegalHoldClientSuccess(listOf(TestUser.OTHER_USER_ID, TestUser.USER_ID))
            .arrange()
        // when
        handler.handleDisable(userId = TestUser.OTHER_USER_ID)
        // then
        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching { it.content is MessageContent.LegalHold.ForConversation.Enabled })
            .wasNotInvoked()
    }
    @Test
    fun givenConversationLegalHoldAlreadyEnabled_whenHandlingEnable_thenDoNotCreateLegalHoldForConversationEnabledMessage() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(
                listOf(TestConversation.CONVERSATION.copy(legalHoldStatus = Conversation.LegalHoldStatus.ENABLED))
            )
            .withMembersHavingLegalHoldClientSuccess(listOf(TestUser.OTHER_USER_ID))
            .arrange()
        // when
        handler.handleEnable(userId = TestUser.OTHER_USER_ID)
        // then
        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching { it.content is MessageContent.LegalHold.ForConversation.Enabled })
            .wasNotInvoked()
    }

    private class Arrangement {

        @Mock
        val membersHavingLegalHoldClient = mock(MembersHavingLegalHoldClientUseCase::class)

        @Mock
        val persistMessage = mock(PersistMessageUseCase::class)

        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val messageRepository = mock(MessageRepository::class)

        init {
            withGetConversationsByUserIdSuccess(emptyList())
            withGetLastMessagesForConversationIdsSuccess(mapOf())
            withMembersHavingLegalHoldClientSuccess(emptyList())
            withUpdateLegalHoldStatusSuccess()
            withUpdateLegalHoldMessageMembersSuccess()
            withPersistMessageSuccess()
            withDeleteLegalHoldRequestSuccess()
        }

        fun arrange() =
            this to LegalHoldSystemMessagesHandlerImpl(
                selfUserId = TestUser.SELF.id,
                membersHavingLegalHoldClient = membersHavingLegalHoldClient,
                persistMessage = persistMessage,
                conversationRepository = conversationRepository,
                messageRepository = messageRepository,
            )

        fun withGetConversationsByUserIdSuccess(conversations: List<Conversation> = emptyList()) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationsByUserId)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(conversations))
        }
        fun withGetLastMessagesForConversationIdsSuccess(result: Map<ConversationId, Message>) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::getLastMessagesForConversationIds)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(result))
        }

        fun withUpdateLegalHoldMessageMembersSuccess() = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::updateLegalHoldMessageMembers)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }
        fun withPersistMessageSuccess() = apply {
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }
        fun withMembersHavingLegalHoldClientSuccess(result: List<UserId>) = apply {
            given(membersHavingLegalHoldClient)
                .suspendFunction(membersHavingLegalHoldClient::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(result))
        }
        fun withUpdateLegalHoldStatusSuccess() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateLegalHoldStatus)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(true))
        }
        fun withDeleteLegalHoldRequestSuccess() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::deleteLegalHoldRequest)
                .whenInvoked()
                .thenReturn(Either.Right(Unit))
        }
    }
    private fun testLegalHoldSystemMessage(content: MessageContent.LegalHold) = Message.System(
        id = TestMessage.TEST_MESSAGE_ID,
        content = content,
        conversationId = ConversationId("conv", "id"),
        date = TestMessage.TEST_DATE_STRING,
        senderUserId = TestMessage.TEST_SENDER_USER_ID,
        status = Message.Status.Pending,
        expirationData = null
    )
}
