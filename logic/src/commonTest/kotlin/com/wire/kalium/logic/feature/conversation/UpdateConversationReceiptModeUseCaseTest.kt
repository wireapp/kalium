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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class UpdateConversationReceiptModeUseCaseTest {

    @Test
    fun givenAReceiptMode_whenUpdatingConversationReceiptMode_thenReturnSuccess() = runTest {
        // given
        val receiptMode = Conversation.ReceiptMode.ENABLED
        val (arrangement, updateConversationReceiptMode) = Arrangement()
            .withPersistingMessage()
            .withUpdateReceiptMode(
                receiptMode,
                Either.Right(Unit)
            )
            .arrange()

        // when
        val result = updateConversationReceiptMode(
            TestConversation.ID,
            receiptMode
        )

        // then
        assertIs<ConversationUpdateReceiptModeResult.Success>(result)

        coVerify {
            arrangement.conversationRepository.updateReceiptMode(eq(TestConversation.ID), eq(receiptMode))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.persistMessage.invoke(
                matches {
                    val message = (it as Message.System)
                    message.content is MessageContent.ConversationReceiptModeChanged
                }
            )
        }.wasInvoked(exactly = once)

    }

    @Test
    fun givenAReceiptMode_whenUpdatingConversationReceiptMode_thenReturnFailure() = runTest {
        // given
        val receiptMode = Conversation.ReceiptMode.ENABLED
        val (arrangement, updateConversationReceiptMode) = Arrangement()
            .withUpdateReceiptMode(
                receiptMode,
                Either.Left(CoreFailure.Unknown(RuntimeException("Error")))
            )
            .arrange()

        // when
        val result = updateConversationReceiptMode(
            TestConversation.ID,
            receiptMode
        )

        // then
        assertIs<ConversationUpdateReceiptModeResult.Failure>(result)

        coVerify {
            arrangement.conversationRepository.updateReceiptMode(eq(TestConversation.ID), eq(receiptMode))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val persistMessage = mock(PersistMessageUseCase::class)

        val selfUserId = TestUser.USER_ID

        private val updateConversationReceiptMode = UpdateConversationReceiptModeUseCaseImpl(
            conversationRepository, persistMessage, selfUserId
        )

        suspend fun withUpdateReceiptMode(receiptMode: Conversation.ReceiptMode, either: Either<CoreFailure, Unit>) = apply {
            coEvery {
                conversationRepository.updateReceiptMode(any(), eq(receiptMode))
            }.returns(either)
        }

        suspend fun withPersistingMessage() = apply {
            coEvery {
                persistMessage.invoke(any())
            }.returns(Either.Right(Unit))
        }

        fun arrange() = this to updateConversationReceiptMode
    }
}
