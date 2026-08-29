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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.conversation.ClearConversationAssetsLocallyUseCase
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

class ClearConversationAssetsLocallyUseCaseAdapterTest {

    @Test
    fun givenDelegateResult_whenClearingAssets_thenSameResultIsReturnedWithExactConversationId() = runTest {
        val expected = Either.Left(StorageFailure.DataNotFound)
        val delegate = mock<ClearConversationAssetsLocallyUseCase>()
        everySuspend { delegate(eq(conversationId)) } returns expected
        val adapter = ClearConversationAssetsLocallyUseCaseAdapter(delegate)

        assertEquals(expected, adapter(conversationId))
        verifySuspend(VerifyMode.exactly(1)) { delegate(eq(conversationId)) }
    }

    @Test
    fun givenDelegateException_whenClearingAssets_thenSameExceptionEscapes() = runTest {
        val expected = IllegalStateException("asset cleanup failed")
        val delegate = mock<ClearConversationAssetsLocallyUseCase>()
        everySuspend { delegate(eq(conversationId)) } throws expected
        val adapter = ClearConversationAssetsLocallyUseCaseAdapter(delegate)

        val actual = try {
            adapter(conversationId)
            error("Expected exception")
        } catch (actual: IllegalStateException) {
            actual
        }

        assertSame(expected, actual)
    }

    @Test
    fun givenDelegateCancellation_whenClearingAssets_thenSameCancellationEscapes() = runTest {
        val expected = CancellationException("asset cleanup cancelled")
        val delegate = mock<ClearConversationAssetsLocallyUseCase>()
        everySuspend { delegate(eq(conversationId)) } throws expected
        val adapter = ClearConversationAssetsLocallyUseCaseAdapter(delegate)

        val actual = try {
            adapter(conversationId)
            error("Expected cancellation")
        } catch (actual: CancellationException) {
            actual
        }

        assertSame(expected, actual)
    }

    private companion object {
        val conversationId = ConversationId("conversation-id", "wire.example")
    }
}
