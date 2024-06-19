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
package com.wire.kalium.logic.feature.message.confirmation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ConfirmationDeliveryHandlerTest {

    @Test
    fun givenANewMessage_whenEnqueuing_thenShouldBeAddedSuccessfullyToTheConversationKey() = runTest {
        val (arrangement, sut) = Arrangement()
            .withCurrentClientIdProvider()
            .arrange()

        sut.enqueueConfirmationDelivery(TestConversation.ID, TestMessage.TEST_MESSAGE_ID)

        assertTrue(arrangement.pendingConfirmationMessages.containsKey(TestConversation.ID))
        assertTrue(arrangement.pendingConfirmationMessages.values.flatten().contains(TestMessage.TEST_MESSAGE_ID))
    }

    private class Arrangement {

        @Mock
        private val currentClientIdProvider = mock(CurrentClientIdProvider::class)

        @Mock
        val syncManager: SyncManager = mock(SyncManager::class)

        @Mock
        val messageSender = mock(MessageSender::class)

        @Mock
        private val conversationRepository = mock(ConversationRepository::class)

        val pendingConfirmationMessages: MutableMap<ConversationId, MutableSet<String>> = mutableMapOf()

        suspend fun withCurrentClientIdProvider() = apply {
            coEvery { currentClientIdProvider.invoke() }.returns(Either.Right(TestClient.CLIENT_ID))
        }

        fun arrange() = this to ConfirmationDeliveryHandlerImpl(
            syncManager = syncManager,
            selfUserId = TestUser.SELF.id,
            currentClientIdProvider = currentClientIdProvider,
            conversationRepository = conversationRepository,
            messageSender = messageSender,
            kaliumLogger = kaliumLogger,
            pendingConfirmationMessages = pendingConfirmationMessages
        )
    }

}
