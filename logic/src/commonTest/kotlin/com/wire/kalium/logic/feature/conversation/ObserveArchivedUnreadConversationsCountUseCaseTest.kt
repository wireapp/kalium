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
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveArchivedUnreadConversationsCountUseCaseTest {

        private val conversationRepository: ConversationRepository = mock()

    private lateinit var observeArchivedUnreadConversationsCount: ObserveArchivedUnreadConversationsCountUseCase

    @BeforeTest
    fun setup() {
        observeArchivedUnreadConversationsCount = ObserveArchivedUnreadConversationsCountUseCaseImpl(conversationRepository)
    }

    @Test
    fun givenArchivedUnreadConversationsCount_whenObserving_thenCorrectCountShouldBeReturned() = runTest {
        val unreadCount = 10L

        everySuspend {
            conversationRepository.observeUnreadArchivedConversationsCount()
        } returns flowOf(unreadCount)

        observeArchivedUnreadConversationsCount().test {
            val result = awaitItem()
            verifySuspend(VerifyMode.exactly(1)) {
                conversationRepository.observeUnreadArchivedConversationsCount()
            }

            assertEquals(unreadCount, result)
            awaitComplete()
        }
    }
}
