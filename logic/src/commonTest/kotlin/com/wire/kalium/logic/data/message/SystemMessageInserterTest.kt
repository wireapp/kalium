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
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage(
                matches { message ->
                    message is Message.System &&
                            message.content is MessageContent.ConversationAppsEnabledChanged &&
                            (message.content as MessageContent.ConversationAppsEnabledChanged).isEnabled
                }
            )
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage(
                matches { message ->
                    message is Message.System &&
                            message.content is MessageContent.ConversationAppsEnabledChanged &&
                            !(message.content as MessageContent.ConversationAppsEnabledChanged).isEnabled
                }
            )
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage(
                matches { message ->
                    message is Message.System && message.id == eventId
                }
            )
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage(
                matches { message ->
                    message is Message.System && message.conversationId == conversationId
                }
            )
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage(
                matches { message ->
                    message is Message.System && message.senderUserId == senderUserId
                }
            )
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage(
                matches { message ->
                    message is Message.System && message.visibility == Message.Visibility.VISIBLE
                }
            )
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage(
                matches { message ->
                    message is Message.System && message.status == Message.Status.Sent
                }
            )
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage(
                matches { message ->
                    message is Message.System && message.expirationData == null
                }
            )
        }
    }

    private class Arrangement {
        val persistMessage = mock<PersistMessageUseCase>(mode = MockMode.autoUnit)

        init {
            runBlocking {
                everySuspend {
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
