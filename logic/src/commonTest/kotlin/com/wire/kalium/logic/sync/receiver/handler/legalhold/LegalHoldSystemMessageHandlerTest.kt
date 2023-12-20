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
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
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
    fun givenNoLastLegalHoldEnabledMessageForConversation_whenHandlingEnableForUser_thenCreateNewSystemMessage() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(TestConversation.CONVERSATION))
            .withGetLastMessagesForConversationIdsSuccess(mapOf(TestConversation.CONVERSATION.id to TestMessage.TEXT_MESSAGE))
            .arrange()
        // when
        handler.handleEnabledForUser(userId = TestUser.OTHER_USER_ID)
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
    fun givenLastLegalHoldEnabledMessageForConversation_whenHandlingEnableForUser_thenUpdateExistingSystemMessage() = runTest {
        // given
        val legalHoldMessage = testLegalHoldSystemMessage(MessageContent.LegalHold.ForMembers.Enabled(listOf(TestUser.OTHER_USER_ID_2)))
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(TestConversation.CONVERSATION))
            .withGetLastMessagesForConversationIdsSuccess(mapOf(TestConversation.CONVERSATION.id to legalHoldMessage))
            .arrange()
        // when
        handler.handleEnabledForUser(userId = TestUser.OTHER_USER_ID)
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
    fun givenNoLastLegalHoldDisabledMessageForConversation_whenHandlingDisableForUser_thenCreateNewSystemMessage() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(TestConversation.CONVERSATION))
            .withGetLastMessagesForConversationIdsSuccess(mapOf(TestConversation.CONVERSATION.id to TestMessage.TEXT_MESSAGE))
            .arrange()
        // when
        handler.handleDisabledForUser(userId = TestUser.OTHER_USER_ID)
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
    fun givenLastLegalHoldDisabledMessageForConversation_whenHandlingDisableForUser_thenUpdateExistingSystemMessage() = runTest {
        // given
        val legalHoldMessage = testLegalHoldSystemMessage(MessageContent.LegalHold.ForMembers.Disabled(listOf(TestUser.OTHER_USER_ID_2)))
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(TestConversation.CONVERSATION))
            .withGetLastMessagesForConversationIdsSuccess(mapOf(TestConversation.CONVERSATION.id to legalHoldMessage))
            .arrange()
        // when
        handler.handleDisabledForUser(userId = TestUser.OTHER_USER_ID)
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
    fun givenConversationId_whenHandlingEnableForConversation_thenCreateNewSystemMessage() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .arrange()
        // when
        handler.handleEnabledForConversation(conversationId = TestConversation.CONVERSATION.id)
        // then
        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching { it.content is MessageContent.LegalHold.ForConversation.Enabled })
            .wasInvoked(exactly = once)
    }
    @Test
    fun givenConversationId_whenHandlingDisableForConversation_thenCreateNewSystemMessage() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .arrange()
        // when
        handler.handleDisabledForConversation(conversationId = TestConversation.CONVERSATION.id)
        // then
        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching { it.content is MessageContent.LegalHold.ForConversation.Disabled })
            .wasInvoked(exactly = once)
    }

    private class Arrangement {


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
            withUpdateLegalHoldMessageMembersSuccess()
            withPersistMessageSuccess()
            withDeleteLegalHoldRequestSuccess()
        }

        fun arrange() =
            this to LegalHoldSystemMessagesHandlerImpl(
                selfUserId = TestUser.SELF.id,
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
