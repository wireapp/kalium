/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DeleteConversationUseCaseAdapterTest {

    @Test
    fun givenDelegateResult_whenDeleting_thenSameResultAndExactArgumentsAreForwarded() = runTest {
        val expected = Either.Left(CoreFailure.Unknown(IllegalStateException("deletion failed")))
        val delegate = mock<DeleteConversationUseCase>()
        everySuspend { delegate(eq(transactionContext), eq(conversationId)) } returns expected
        val adapter = DeleteConversationUseCaseAdapter(delegate)

        assertEquals(expected, adapter(transactionContext, conversationId))
        verifySuspend(VerifyMode.exactly(1)) {
            delegate(eq(transactionContext), eq(conversationId))
        }
    }

    @Test
    fun givenDelegateException_whenDeleting_thenSameExceptionEscapes() = runTest {
        val expected = IllegalStateException("deletion failed")
        val delegate = mock<DeleteConversationUseCase>()
        everySuspend { delegate(eq(transactionContext), eq(conversationId)) } throws expected
        val adapter = DeleteConversationUseCaseAdapter(delegate)

        val actual = try {
            adapter(transactionContext, conversationId)
            error("Expected exception")
        } catch (actual: IllegalStateException) {
            actual
        }

        assertSame(expected, actual)
    }

    @Test
    fun givenDelegateCancellation_whenDeleting_thenSameCancellationEscapes() = runTest {
        val expected = CancellationException("deletion cancelled")
        val delegate = mock<DeleteConversationUseCase>()
        everySuspend { delegate(eq(transactionContext), eq(conversationId)) } throws expected
        val adapter = DeleteConversationUseCaseAdapter(delegate)

        val actual = try {
            adapter(transactionContext, conversationId)
            error("Expected cancellation")
        } catch (actual: CancellationException) {
            actual
        }

        assertSame(expected, actual)
    }

    private companion object {
        val transactionContext = mock<CryptoTransactionContext>(MockMode.autoUnit)
        val conversationId = ConversationId("conversation-id", "wire.example")
    }
}
