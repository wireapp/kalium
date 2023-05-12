/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetConversationUnreadEventsCountUseCaseTest {
    @Mock
    private val conversationRepository = mock(classOf<ConversationRepository>())

    private lateinit var getConversationUnreadEventsCountUseCase: GetConversationUnreadEventsCountUseCase

    @BeforeTest
    fun setUp() {
        getConversationUnreadEventsCountUseCase = GetConversationUnreadEventsCountUseCaseImpl(conversationRepository)
    }

    @Test
    fun givenGettingUnreadEventsCountSucceed_whenItIsRequested_thenSuccessResultReturned() = runTest {
        // given
        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationUnreadEventsCount)
            .whenInvokedWith(anything())
            .thenReturn(Either.Right(1))

        // when
        val result = getConversationUnreadEventsCountUseCase(ConversationId(value = "convId", domain = "domainId"))

        // then
        assertEquals(GetConversationUnreadEventsCountUseCase.Result.Success(1), result)
    }

    @Test
    fun givenGettingUnreadEventsCountFailed_whenItIsRequested_thenFailureResultReturned() = runTest {
        // given
        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationUnreadEventsCount)
            .whenInvokedWith(anything())
            .thenReturn(Either.Left(StorageFailure.DataNotFound))

        // when
        val result = getConversationUnreadEventsCountUseCase(ConversationId(value = "convId", domain = "domainId"))

        // then
        assertEquals(GetConversationUnreadEventsCountUseCase.Result.Failure(StorageFailure.DataNotFound), result)
    }
}
