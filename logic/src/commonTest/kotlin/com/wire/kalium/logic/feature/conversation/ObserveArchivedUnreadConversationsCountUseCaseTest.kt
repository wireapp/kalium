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

import app.cash.turbine.test
import com.wire.kalium.logic.data.conversation.ConversationRepository
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveArchivedUnreadConversationsCountUseCaseTest {

        private val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

    private lateinit var observeArchivedUnreadConversationsCount: ObserveArchivedUnreadConversationsCountUseCase

    @BeforeTest
    fun setup() {
        observeArchivedUnreadConversationsCount = ObserveArchivedUnreadConversationsCountUseCaseImpl(conversationRepository)
    }

    @Test
    fun givenArchivedUnreadConversationsCount_whenObserving_thenCorrectCountShouldBeReturned() = runTest {
        // Given
        val unreadCount = 10L

        coEvery {
            conversationRepository.observeUnreadArchivedConversationsCount()
        }.returns(flowOf(unreadCount))

        // When
        observeArchivedUnreadConversationsCount().test {
            // Then
            val result = awaitItem()
            coVerify {
                conversationRepository.observeUnreadArchivedConversationsCount()
            }.wasInvoked(exactly = once)

            assertEquals(unreadCount, result)
            awaitComplete()
        }
    }
}
