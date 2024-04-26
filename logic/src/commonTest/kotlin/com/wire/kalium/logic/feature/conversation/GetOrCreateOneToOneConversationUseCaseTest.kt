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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneResolverArrangement
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneResolverArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import io.mockative.any
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

@Suppress("MaxLineLength")
class GetOrCreateOneToOneConversationUseCaseTest {

    @Test
    fun givenConversationExist_whenCallingTheUseCase_ThenReturnExistingConversation() = runTest {
        // given
        val (arrangement, useCase) = arrange {
            withObserveOneToOneConversationWithOtherUserReturning(Either.Right(CONVERSATION))
        }

        // when
        val result = useCase.invoke(OTHER_USER_ID)

        // then
        assertIs<CreateConversationResult.Success>(result)

        coVerify {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUser(any(), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.conversationRepository.observeOneToOneConversationWithOtherUser(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenFailure_whenCallingTheUseCase_ThenErrorIsPropagated() = runTest {
        // given
        val (_, useCase) = arrange {
            withObserveOneToOneConversationWithOtherUserReturning(Either.Left(StorageFailure.DataNotFound))
            withUserByIdReturning(OTHER_USER.right())
            withResolveOneOnOneConversationWithUserReturning(Either.Left(CoreFailure.NoCommonProtocolFound))
        }

        // when
        val result = useCase.invoke(OTHER_USER_ID)

        // then
        assertIs<CreateConversationResult.Failure>(result)
    }

    @Test
    fun givenFailureWhileGettingUser_whenCallingTheUseCase_ThenErrorIsPropagated() = runTest {
        // given
        val (_, useCase) = arrange {
            withObserveOneToOneConversationWithOtherUserReturning(Either.Left(StorageFailure.DataNotFound))
            withUserByIdReturning(Either.Left(StorageFailure.DataNotFound))
        }

        // when
        val result = useCase.invoke(OTHER_USER_ID)

        // then
        assertIs<CreateConversationResult.Failure>(result)
    }

    @Test
    fun givenConversationDoesNotExist_whenCallingTheUseCase_ThenResolveOneOnOneConversation() = runTest {
        // given
        val (arrangement, useCase) = arrange {
            withObserveOneToOneConversationWithOtherUserReturning(Either.Left(StorageFailure.DataNotFound))
            withUserByIdReturning(OTHER_USER.right())
            withResolveOneOnOneConversationWithUserReturning(Either.Right(CONVERSATION.id))
        }

        // when
        val result = useCase.invoke(OTHER_USER_ID)

        // then
        assertIs<CreateConversationResult.Success>(result)

        coVerify {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUser(eq(OTHER_USER), any())
        }.wasInvoked(exactly = once)
    }

    private suspend fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    internal class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        UserRepositoryArrangement by UserRepositoryArrangementImpl(),
        OneOnOneResolverArrangement by OneOnOneResolverArrangementImpl() {

        suspend fun arrange() = run {
            block()
            this@Arrangement to GetOrCreateOneToOneConversationUseCaseImpl(
                conversationRepository = conversationRepository,
                userRepository = userRepository,
                oneOnOneResolver = oneOnOneResolver
            )
        }
    }

    private companion object {
        val OTHER_USER = TestUser.OTHER
        val OTHER_USER_ID = OTHER_USER.id
        val CONVERSATION = TestConversation.ONE_ON_ONE()
    }
}
