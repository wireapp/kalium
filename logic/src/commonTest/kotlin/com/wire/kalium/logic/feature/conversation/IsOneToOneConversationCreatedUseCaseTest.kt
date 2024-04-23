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
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsOneToOneConversationCreatedUseCaseTest {

    @Test
    fun givenConversationExist_whenCallingTheUseCase_ThenReturnTrue() = runTest {
        // given
        val (_, useCase) = arrange {
            withObserveOneToOneConversationWithOtherUserReturning(Either.Right(TestConversation.ONE_ON_ONE()))
        }

        // when
        val result = useCase.invoke(TestUser.OTHER_USER_ID)

        // then
        assertTrue(result)
    }

    @Test
    fun givenNotConversationExist_whenCallingTheUseCase_ThenReturnFalse() = runTest {
        // given
        val (_, useCase) = arrange {
            withObserveOneToOneConversationWithOtherUserReturning(Either.Left(StorageFailure.DataNotFound))
        }

        // when
        val result = useCase.invoke(TestUser.OTHER_USER_ID)

        // then
        assertFalse(result)
    }

    private fun arrange(block: Arrangement.() -> Unit) = Arrangement(block).arrange()

    internal class Arrangement(
        private val block: Arrangement.() -> Unit
    ) : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl() {

        fun arrange() = block().run {
            this@Arrangement to IsOneToOneConversationCreatedUseCaseImpl(
                conversationRepository = conversationRepository
            )
        }
    }
}
