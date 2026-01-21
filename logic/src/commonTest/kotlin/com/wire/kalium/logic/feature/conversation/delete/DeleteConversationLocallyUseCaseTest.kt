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
package com.wire.kalium.logic.feature.conversation.delete

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.conversation.ClearConversationContentUseCase
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.DeleteConversationArrangement
import com.wire.kalium.logic.util.arrangement.usecase.DeleteConversationArrangementImpl
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class DeleteConversationLocallyUseCaseTest {

    companion object {
        val ERROR = Either.Left(CoreFailure.Unknown(null))
        val CONVERSATION_ID = ConversationId("someValue", "someDomain")
    }

    @Test
    fun givenDeleteLocalConversationInvoked_whenAllStepsAreSuccessful_thenSuccessResultIsPropagated() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withClearLocalAsset(true)
            .arrange {
                withDeletingConversationSucceeding()
            }

        // when
        val result = useCase(CONVERSATION_ID)

        // then
        assertIs<ClearConversationContentUseCase.Result.Success>(result)
        coVerify { arrangement.clearConversationContent(any(), eq(true)) }.wasInvoked(exactly = 1)
    }

    @Test
    fun givenDeleteLocalConversationInvoked_whenClearContentIsUnsuccessful_thenErrorResultIsPropagated() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withClearLocalAsset(false)
            .arrange {
                withDeletingConversationSucceeding()
            }

        // when
        val result = useCase(CONVERSATION_ID)

        // then
        assertIs<ClearConversationContentUseCase.Result.Failure>(result)
        coVerify { arrangement.clearConversationContent(any(), eq(true)) }.wasInvoked(exactly = 1)
    }

    private class Arrangement : DeleteConversationArrangement by DeleteConversationArrangementImpl(),
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val clearConversationContent = mock(ClearConversationContentUseCase::class)

        suspend fun withClearLocalAsset(isSuccess: Boolean) = apply {
            coEvery { clearConversationContent(any(), any()) }.returns(
                if (isSuccess) ClearConversationContentUseCase.Result.Success
                else ClearConversationContentUseCase.Result.Failure(ERROR.value)
            )
        }

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, DeleteConversationLocallyUseCase> = run {
            val useCase = DeleteConversationLocallyUseCaseImpl(clearConversationContent = clearConversationContent)
            block()
            this to useCase
        }
    }
}
