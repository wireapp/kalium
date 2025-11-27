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
package com.wire.kalium.conversation.history.domain

import app.cash.turbine.test
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.MockConversation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsConversationHistorySupportedForConversationUseCaseTest {

    @Test
    fun givenConversationIsChannelAndEverythingElseIsEnabled_whenInvoking_thenShouldReturnTrue() = runTest {
        val subject = IsConversationHistorySupportedForConversationUseCase(
            conversationByIdProvider = { flowOf(Either.Right(MockConversation.channel())) },
            isBuildTimeAllowed = true
        )
        
        subject(MockConversation.id()).test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenConversationIsChannelButDisallowed_whenInvoking_thenShouldReturnFalse() = runTest {
        val subject = IsConversationHistorySupportedForConversationUseCase(
            conversationByIdProvider = { flowOf(Either.Right(MockConversation.channel())) },
            isBuildTimeAllowed = false
        )

        subject(MockConversation.id()).test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenConversationIsOneOnOneAndEverythingElseIsEnabled_whenInvoking_thenShouldReturnFalse() = runTest {
        val subject = IsConversationHistorySupportedForConversationUseCase(
            conversationByIdProvider = { flowOf(Either.Right(MockConversation.oneOnOne())) },
            isBuildTimeAllowed = true
        )

        subject(MockConversation.id()).test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenConversationIsGroupAndEverythingElseIsEnabled_whenInvoking_thenShouldReturnFalse() = runTest {
        val subject = IsConversationHistorySupportedForConversationUseCase(
            conversationByIdProvider = { flowOf(Either.Right(MockConversation.group())) },
            isBuildTimeAllowed = true
        )

        subject(MockConversation.id()).test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
