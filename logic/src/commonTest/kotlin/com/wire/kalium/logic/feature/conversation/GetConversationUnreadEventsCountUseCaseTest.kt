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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetConversationUnreadEventsCountUseCaseTest {
    @Mock
    private val conversationRepository = mock(ConversationRepository::class)

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
        // given
        coEvery {
            conversationRepository.getConversationUnreadEventsCount(any())
        }.returns(Either.Right(1))

        // when
        val result = getConversationUnreadEventsCountUseCase(ConversationId(value = "convId", domain = "domainId"))

        // then
        assertEquals(GetConversationUnreadEventsCountUseCase.Result.Success(1), result)
    }

    @Test
    fun givenGettingUnreadEventsCountFailed_whenItIsRequested_thenFailureResultReturned() = runTest(TestKaliumDispatcher.main) {
        // given
        coEvery {
            conversationRepository.getConversationUnreadEventsCount(any())
        }.returns(Either.Left(StorageFailure.DataNotFound))

        // when
        val result = getConversationUnreadEventsCountUseCase(ConversationId(value = "convId", domain = "domainId"))

        // then
        assertEquals(GetConversationUnreadEventsCountUseCase.Result.Failure(StorageFailure.DataNotFound), result)
    }
}
