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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TypingIndicatorSenderHandlerTest {

    @Test
    fun givenStartedFails_whenTypingAgainWithinTimeout_thenSendOnlyOneRequest() = runTest {
        val conversationRepository = mock<ConversationRepository>()
        everySuspend {
            conversationRepository.sendTypingIndicatorStatus(any(), any())
        } returns Either.Left(CoreFailure.Unknown(RuntimeException("Offline")))
        val handler = TypingIndicatorSenderHandlerImpl(
            conversationRepository = conversationRepository,
            kaliumDispatcher = StandardTestDispatcher(testScheduler).testKaliumDispatcher(),
            userSessionCoroutineScope = backgroundScope
        )

        handler.sendStartedAndEnqueueStoppingEvent(TestConversation.ID)
        runCurrent()
        repeat(9) {
            advanceTimeBy(1_000)
            handler.sendStartedAndEnqueueStoppingEvent(TestConversation.ID)
            runCurrent()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            conversationRepository.sendTypingIndicatorStatus(TestConversation.ID, any())
        }
    }
}
