/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.message

import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.framework.TestUser
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SystemMessageInserterTest {

    @Test
    fun givenAppsAccessEnabledTrue_whenInsertingSystemMessage_thenMessageIsPersistedWithCorrectContent() = runTest {
        // Given
        val conversationId = MockConversation.ID
        val senderUserId = TestUser.SELF.id
        val isAppsAccessEnabled = true

        val (arrangement, systemMessageInserter) = Arrangement()
            .arrange()

        // When
        systemMessageInserter.insertConversationAppsAccessChanged(
            conversationId = conversationId,
            senderUserId = senderUserId,
            isAppsAccessEnabled = isAppsAccessEnabled
        )

        // Then
        coVerify {
            arrangement.persistMessage(
                matches { message ->
                    message is Message.System &&
                            message.content is MessageContent.ConversationAppsEnabledChanged &&
                            (message.content as MessageContent.ConversationAppsEnabledChanged).isEnabled
                }
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAppsAccessEnabledFalse_whenInsertingSystemMessage_thenMessageIsPersistedWithCorrectContent() = runTest {
        // Given
        val conversationId = MockConversation.ID
        val senderUserId = TestUser.SELF.id
        val isAppsAccessEnabled = false

        val (arrangement, systemMessageInserter) = Arrangement()
            .arrange()

        // When
        systemMessageInserter.insertConversationAppsAccessChanged(
            conversationId = conversationId,
            senderUserId = senderUserId,
            isAppsAccessEnabled = isAppsAccessEnabled
        )

        // Then
        coVerify {
            arrangement.persistMessage(
                matches { message ->
                    message is Message.System &&
                            message.content is MessageContent.ConversationAppsEnabledChanged &&
                            !(message.content as MessageContent.ConversationAppsEnabledChanged).isEnabled
                }
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenEventId_whenInsertingSystemMessage_thenMessageUsesProvidedEventId() = runTest {
        // Given
        val eventId = "custom-event-id-456"
        val conversationId = MockConversation.ID
        val senderUserId = TestUser.SELF.id

        val (arrangement, systemMessageInserter) = Arrangement()
            .arrange()

        // When
        systemMessageInserter.insertConversationAppsAccessChanged(
            eventId = eventId,
            conversationId = conversationId,
            senderUserId = senderUserId,
            isAppsAccessEnabled = true
        )

        // Then
        coVerify {
            arrangement.persistMessage(
                matches { message ->
                    message is Message.System && message.id == eventId
                }
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenConversationId_whenInsertingSystemMessage_thenMessageHasCorrectConversationId() = runTest {
        // Given
        val conversationId = MockConversation.ID
        val senderUserId = TestUser.SELF.id

        val (arrangement, systemMessageInserter) = Arrangement()
            .arrange()

        // When
        systemMessageInserter.insertConversationAppsAccessChanged(
            conversationId = conversationId,
            senderUserId = senderUserId,
            isAppsAccessEnabled = true
        )

        // Then
        coVerify {
            arrangement.persistMessage(
                matches { message ->
                    message is Message.System && message.conversationId == conversationId
                }
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSenderUserId_whenInsertingSystemMessage_thenMessageHasCorrectSender() = runTest {
        // Given
        val conversationId = MockConversation.ID
        val senderUserId = TestUser.OTHER_USER_ID

        val (arrangement, systemMessageInserter) = Arrangement()
            .arrange()

        // When
        systemMessageInserter.insertConversationAppsAccessChanged(
            conversationId = conversationId,
            senderUserId = senderUserId,
            isAppsAccessEnabled = true
        )

        // Then
        coVerify {
            arrangement.persistMessage(
                matches { message ->
                    message is Message.System && message.senderUserId == senderUserId
                }
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSystemMessage_whenInserting_thenMessageVisibilityIsVisible() = runTest {
        // Given
        val conversationId = MockConversation.ID
        val senderUserId = TestUser.SELF.id

        val (arrangement, systemMessageInserter) = Arrangement()
            .arrange()

        // When
        systemMessageInserter.insertConversationAppsAccessChanged(
            conversationId = conversationId,
            senderUserId = senderUserId,
            isAppsAccessEnabled = true
        )

        // Then
        coVerify {
            arrangement.persistMessage(
                matches { message ->
                    message is Message.System && message.visibility == Message.Visibility.VISIBLE
                }
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSystemMessage_whenInserting_thenMessageStatusIsSent() = runTest {
        // Given
        val conversationId = MockConversation.ID
        val senderUserId = TestUser.SELF.id

        val (arrangement, systemMessageInserter) = Arrangement()
            .arrange()

        // When
        systemMessageInserter.insertConversationAppsAccessChanged(
            conversationId = conversationId,
            senderUserId = senderUserId,
            isAppsAccessEnabled = true
        )

        // Then
        coVerify {
            arrangement.persistMessage(
                matches { message ->
                    message is Message.System && message.status == Message.Status.Sent
                }
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSystemMessage_whenInserting_thenExpirationDataIsNull() = runTest {
        // Given
        val conversationId = MockConversation.ID
        val senderUserId = TestUser.SELF.id

        val (arrangement, systemMessageInserter) = Arrangement()
            .arrange()

        // When
        systemMessageInserter.insertConversationAppsAccessChanged(
            conversationId = conversationId,
            senderUserId = senderUserId,
            isAppsAccessEnabled = true
        )

        // Then
        coVerify {
            arrangement.persistMessage(
                matches { message ->
                    message is Message.System && message.expirationData == null
                }
            )
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        val persistMessage = mock(PersistMessageUseCase::class)

        init {
            runBlocking {
                coEvery {
                    persistMessage(any())
                }.returns(Unit.right())
            }
        }

        private val systemMessageInserter = SystemMessageInserterImpl(
            persistMessage = persistMessage,
            selfUserId = TestUser.SELF.id
        )

        fun arrange() = this to systemMessageInserter
    }
}
