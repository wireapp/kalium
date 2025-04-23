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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
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
        handler.handleEnabledForUser(userId = TestUser.OTHER_USER_ID, Instant.UNIX_FIRST_DATE)
        // then
        coVerify {
            arrangement.persistMessage.invoke(matches {
                it.content is MessageContent.LegalHold.ForMembers.Enabled && it.date == Instant.UNIX_FIRST_DATE
                        && (it.content as MessageContent.LegalHold.ForMembers.Enabled).members == listOf(TestUser.OTHER_USER_ID)
            })
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.messageRepository.updateLegalHoldMessageMembers(any(), any(), any())
        }.wasNotInvoked()
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
        handler.handleEnabledForUser(userId = TestUser.OTHER_USER_ID, Instant.UNIX_FIRST_DATE)
        // then
        coVerify {
            arrangement.persistMessage.invoke(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.messageRepository.updateLegalHoldMessageMembers(
                any(),
                any(),
                matches { it == listOf(TestUser.OTHER_USER_ID_2, TestUser.OTHER_USER_ID) }
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenNoLastLegalHoldDisabledMessageForConversation_whenHandlingDisableForUser_thenCreateNewSystemMessage() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withGetConversationsByUserIdSuccess(listOf(TestConversation.CONVERSATION))
            .withGetLastMessagesForConversationIdsSuccess(mapOf(TestConversation.CONVERSATION.id to TestMessage.TEXT_MESSAGE))
            .arrange()
        // when
        handler.handleDisabledForUser(userId = TestUser.OTHER_USER_ID, Instant.UNIX_FIRST_DATE)
        // then
        coVerify {
            arrangement.persistMessage.invoke(
                matches {
                    it.content is MessageContent.LegalHold.ForMembers.Disabled && it.date == Instant.UNIX_FIRST_DATE
                            && (it.content as MessageContent.LegalHold.ForMembers.Disabled).members == listOf(TestUser.OTHER_USER_ID)
                }
            )
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.messageRepository.updateLegalHoldMessageMembers(any(), any(), any())
        }.wasNotInvoked()
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
        handler.handleDisabledForUser(userId = TestUser.OTHER_USER_ID, Instant.UNIX_FIRST_DATE)
        // then
        coVerify {
            arrangement.persistMessage.invoke(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.messageRepository.updateLegalHoldMessageMembers(
                any(),
                any(),
                matches { it == listOf(TestUser.OTHER_USER_ID_2, TestUser.OTHER_USER_ID) })
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenConversationId_whenHandlingEnableForConversation_thenCreateNewSystemMessage() = runTest {
        // given
        val (arrangement, handler) = Arrangement().arrange()
        // when
        handler.handleEnabledForConversation(conversationId = TestConversation.CONVERSATION.id, Instant.UNIX_FIRST_DATE)
        // then
        coVerify {
            arrangement.persistMessage.invoke(matches { it.content is MessageContent.LegalHold.ForConversation.Enabled && it.date == Instant.UNIX_FIRST_DATE })
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenConversationId_whenHandlingDisableForConversation_thenCreateNewSystemMessage() = runTest {
        // given
        val (arrangement, handler) = Arrangement().arrange()
        // when
        handler.handleDisabledForConversation(conversationId = TestConversation.CONVERSATION.id, Instant.UNIX_FIRST_DATE)
        // then
        coVerify {
            arrangement.persistMessage.invoke(matches { it.content is MessageContent.LegalHold.ForConversation.Disabled && it.date == Instant.UNIX_FIRST_DATE })
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        val persistMessage = mock(PersistMessageUseCase::class)
        val userConfigRepository = mock(UserConfigRepository::class)
        val conversationRepository = mock(ConversationRepository::class)
        val messageRepository = mock(MessageRepository::class)

        init {
            runBlocking {
                withGetConversationsByUserIdSuccess(emptyList())
                withGetLastMessagesForConversationIdsSuccess(mapOf())
                withUpdateLegalHoldMessageMembersSuccess()
                withPersistMessageSuccess()
                withDeleteLegalHoldRequestSuccess()
            }
        }

        fun arrange() = this to LegalHoldSystemMessagesHandlerImpl(
            selfUserId = TestUser.SELF.id,
            persistMessage = persistMessage,
            conversationRepository = conversationRepository,
            messageRepository = messageRepository,
        )

        suspend fun withGetConversationsByUserIdSuccess(conversations: List<Conversation> = emptyList()) = apply {
            coEvery {
                conversationRepository.getConversationsByUserId(any())
            }.returns(Either.Right(conversations))
        }

        suspend fun withGetLastMessagesForConversationIdsSuccess(result: Map<ConversationId, Message>) = apply {
            coEvery {
                messageRepository.getLastMessagesForConversationIds(any())
            }.returns(Either.Right(result))
        }

        suspend fun withUpdateLegalHoldMessageMembersSuccess() = apply {
            coEvery {
                messageRepository.updateLegalHoldMessageMembers(any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withPersistMessageSuccess() = apply {
            coEvery {
                persistMessage.invoke(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withDeleteLegalHoldRequestSuccess() = apply {
            coEvery {
                userConfigRepository.deleteLegalHoldRequest()
            }.returns(Either.Right(Unit))
        }
    }

    private fun testLegalHoldSystemMessage(content: MessageContent.LegalHold) = Message.System(
        id = TestMessage.TEST_MESSAGE_ID,
        content = content,
        conversationId = ConversationId("conv", "id"),
        date = TestMessage.TEST_DATE,
        senderUserId = TestMessage.TEST_SENDER_USER_ID,
        status = Message.Status.Pending,
        expirationData = null
    )
}
