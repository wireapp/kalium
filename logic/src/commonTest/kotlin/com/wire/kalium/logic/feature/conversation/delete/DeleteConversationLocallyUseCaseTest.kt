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
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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
        val (arrangement, useCase) = Arrangement()
            .withClearLocalAsset(true)
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertIs<ClearConversationContentUseCase.Result.Success>(result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.clearConversationContent(any(), eq(true)) }
    }

    @Test
    fun givenDeleteLocalConversationInvoked_whenClearContentIsUnsuccessful_thenErrorResultIsPropagated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withClearLocalAsset(false)
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertIs<ClearConversationContentUseCase.Result.Failure>(result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.clearConversationContent(any(), eq(true)) }
    }

    private class Arrangement {

        val clearConversationContent = mock<ClearConversationContentUseCase>(mode = MockMode.autoUnit)

        suspend fun withClearLocalAsset(isSuccess: Boolean) = apply {
            everySuspend { clearConversationContent(any(), any()) } returns (
                if (isSuccess) ClearConversationContentUseCase.Result.Success
                else ClearConversationContentUseCase.Result.Failure(ERROR.value)
            )
        }

        fun arrange(): Pair<Arrangement, DeleteConversationLocallyUseCase> = run {
            val useCase = DeleteConversationLocallyUseCaseImpl(clearConversationContent = clearConversationContent)
            this to useCase
        }
    }
}
