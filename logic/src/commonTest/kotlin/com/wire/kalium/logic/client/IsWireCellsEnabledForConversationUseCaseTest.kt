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
package com.wire.kalium.logic.client

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.client.IsWireCellsEnabledForConversationUseCaseImpl
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsWireCellsEnabledForConversationUseCaseTest {

    @Test
    fun given_isCellsConversation_is_true_when_invoked_then_returns_true() = runTest {
        val (_, useCase) = Arrangement()
            .withCellsEnabledForConversationReturning(Either.Right(true))
            .arrange()

        val result = useCase.invoke(conversationId)

        assertTrue(result)
    }

    @Test
    fun given_isCellsConversation_is_false_when_invoked_then_returns_false() = runTest {
        val (_, useCase) = Arrangement()
            .withCellsEnabledForConversationReturning(Either.Right(false))
            .arrange()

        val result = useCase.invoke(conversationId)

        assertFalse(result)
    }

    @Test
    fun given_isCellsConversation_throws_error_when_invoked_then_returns_false() = runTest {
        val (_, useCase) = Arrangement()
            .withCellsEnabledForConversationReturning(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        val result = useCase.invoke(conversationId)

        assertFalse(result)
    }

    private class Arrangement {

        private val conversationRepository = mock(ConversationRepository::class)

        suspend fun withCellsEnabledForConversationReturning(result: Either<StorageFailure, Boolean>) = apply {
            coEvery { conversationRepository.isCellEnabled(conversationId) } returns result
        }

        fun arrange() = this to IsWireCellsEnabledForConversationUseCaseImpl(
            conversationRepository = conversationRepository
        )
    }

    companion object {
        val conversationId = ConversationId("value", "domain")
    }
}
