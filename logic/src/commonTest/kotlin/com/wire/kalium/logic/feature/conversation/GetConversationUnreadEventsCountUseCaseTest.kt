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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetConversationUnreadEventsCountUseCaseTest {
        private val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)

    private lateinit var getConversationUnreadEventsCountUseCase: GetConversationUnreadEventsCountUseCase

    @BeforeTest
    fun setUp() {
        getConversationUnreadEventsCountUseCase = GetConversationUnreadEventsCountUseCaseImpl(
            conversationRepository,
            TestKaliumDispatcher
        )
    }

    @Test
    fun givenGettingUnreadEventsCountSucceed_whenItIsRequested_thenSuccessResultReturned() = runTest(TestKaliumDispatcher.main) {
        everySuspend {
            conversationRepository.getConversationUnreadEventsCount(any())
        } returns Either.Right(1)

        val result = getConversationUnreadEventsCountUseCase(ConversationId(value = "convId", domain = "domainId"))

        assertEquals(GetConversationUnreadEventsCountUseCase.Result.Success(1), result)
    }

    @Test
    fun givenGettingUnreadEventsCountFailed_whenItIsRequested_thenFailureResultReturned() = runTest(TestKaliumDispatcher.main) {
        everySuspend {
            conversationRepository.getConversationUnreadEventsCount(any())
        } returns Either.Left(StorageFailure.DataNotFound)

        val result = getConversationUnreadEventsCountUseCase(ConversationId(value = "convId", domain = "domainId"))

        assertEquals(GetConversationUnreadEventsCountUseCase.Result.Failure(StorageFailure.DataNotFound), result)
    }
}
