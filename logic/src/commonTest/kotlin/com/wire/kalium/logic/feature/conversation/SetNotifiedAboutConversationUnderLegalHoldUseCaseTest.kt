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
import com.wire.kalium.common.functional.Either
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.setLegalHoldStatusChangeNotified(eq(conversationId))
        }
    }

    private class Arrangement {

        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)

        private val useCase: SetNotifiedAboutConversationUnderLegalHoldUseCase by lazy {
            SetNotifiedAboutConversationUnderLegalHoldUseCaseImpl(conversationRepository)
        }
        fun arrange() = this to useCase
        suspend fun withSetLegalHoldStatusChangeNotifiedSuccessful() = apply {
            everySuspend {
                conversationRepository.setLegalHoldStatusChangeNotified(any())
            } returns Either.Right(true)
        }
    }
}
