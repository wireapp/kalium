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

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SetNotifiedAboutConversationUnderLegalHoldUseCaseTest {

    @Test
    fun givenConversationId_whenInvoke_thenRepositoryIsCalledCorrectly() = runTest {
        // given
        val conversationId = ConversationId("conversationId", "domain")
        val (arrangement, useCase) = Arrangement()
            .withSetLegalHoldStatusChangeNotifiedSuccessful()
            .arrange()
        // when
        useCase.invoke(conversationId)
        // then
        coVerify {
            arrangement.conversationRepository.setLegalHoldStatusChangeNotified(eq(conversationId))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        private val useCase: SetNotifiedAboutConversationUnderLegalHoldUseCase by lazy {
            SetNotifiedAboutConversationUnderLegalHoldUseCaseImpl(conversationRepository)
        }
        fun arrange() = this to useCase
        suspend fun withSetLegalHoldStatusChangeNotifiedSuccessful() = apply {
            coEvery {
                conversationRepository.setLegalHoldStatusChangeNotified(any())
            }.returns(Either.Right(true))
        }
    }
}
