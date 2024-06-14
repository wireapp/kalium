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
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveConversationUnderLegalHoldNotifiedUseCaseTest {

    private fun testObserving(
        given: Either<StorageFailure, Pair<Conversation.LegalHoldStatus, Boolean>>,
        expected: Boolean
    ) = runTest {
        // given
        val conversationId = ConversationId("conversationId", "domain")
        val (_, useCase) = Arrangement()
            .withObserveLegalHoldStatusForConversation(given.map { it.first })
            .withObserveLegalHoldStatusChangeNotifiedForConversation(given.map { it.second })
            .arrange()
        // when
        val result = useCase.invoke(conversationId)
        // then
        assertEquals(expected, result.first())
    }

    @Test
    fun givenFailure_whenObserving_thenReturnTrue() =
        testObserving(Either.Left(StorageFailure.DataNotFound), true)

    @Test
    fun givenLegalHoldEnabledAndNotNotified_whenObserving_thenReturnFalse() =
        testObserving(Either.Right(Conversation.LegalHoldStatus.ENABLED to false), false)

    @Test
    fun givenLegalHoldEnabledAndNotified_whenObserving_thenReturnTrue() =
        testObserving(Either.Right(Conversation.LegalHoldStatus.ENABLED to true), true)

    @Test
    fun givenLegalHoldDisabledAndNotNotified_whenObserving_thenReturnFalse() =
        testObserving(Either.Right(Conversation.LegalHoldStatus.DISABLED to false), true)

    @Test
    fun givenLegalHoldDisabledAndNotified_whenObserving_thenReturnTrue() =
        testObserving(Either.Right(Conversation.LegalHoldStatus.DISABLED to true), true)

    private class Arrangement {
        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        private val useCase: ObserveConversationUnderLegalHoldNotifiedUseCase by lazy {
            ObserveConversationUnderLegalHoldNotifiedUseCaseImpl(conversationRepository)
        }

        fun arrange() = this to useCase
        suspend fun withObserveLegalHoldStatusForConversation(
            result: Either<StorageFailure, Conversation.LegalHoldStatus>
        ) = apply {
            coEvery {
                conversationRepository.observeLegalHoldStatus(any())
            }.returns(flowOf(result))
        }

        suspend fun withObserveLegalHoldStatusChangeNotifiedForConversation(
            result: Either<StorageFailure, Boolean>
        ) = apply {
            coEvery {
                conversationRepository.observeLegalHoldStatusChangeNotified(any())
            }.returns(flowOf(result))
        }
    }
}
