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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.protocol.OneOnOneProtocolSelectorArrangement
import com.wire.kalium.logic.util.arrangement.protocol.OneOnOneProtocolSelectorArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationGroupRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationGroupRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import io.mockative.anything
import io.mockative.eq
import io.mockative.once
import io.mockative.verify
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
        val result = useCase.invoke(USER_ID)

        // then
        assertIs<CreateConversationResult.Success>(result)

        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::createGroupConversation)
            .with(anything(), anything(), anything())
            .wasNotInvoked()

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::observeOneToOneConversationWithOtherUser)
            .with(anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenFailure_whenCallingTheUseCase_ThenErrorIsPropagated() = runTest {
        // given
        val (_, useCase) =  arrange {
            withObserveOneToOneConversationWithOtherUserReturning(Either.Left(StorageFailure.DataNotFound))
            withGetProtocolForUser(Either.Left(CoreFailure.NoCommonProtocolFound))
        }

        // when
        val result = useCase.invoke(USER_ID)

        // then
        assertIs<CreateConversationResult.Failure>(result)
    }

    @Test
    fun givenConversationDoesNotExistWithProteusAsProtocol_whenCallingTheUseCase_ThenCreateGroupConversation() = runTest {
        // given
        val (arrangement, useCase) = arrange {
            withObserveOneToOneConversationWithOtherUserReturning(Either.Left(StorageFailure.DataNotFound))
            withGetProtocolForUser(Either.Right(SupportedProtocol.PROTEUS))
            withCreateGroupConversationReturning(Either.Right(CONVERSATION))
        }

        // when
        val result = useCase.invoke(USER_ID)

        // then
        assertIs<CreateConversationResult.Success>(result)

        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::createGroupConversation)
            .with(eq(null), eq(MEMBER), eq(ConversationOptions()))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenConversationDoesNotExistWithMlsAsProtocol_whenCallingTheUseCase_thenFetchMlsOneToOne() = runTest {
        // given
        val (arrangement, useCase) = arrange {
            withObserveOneToOneConversationWithOtherUserReturning(Either.Left(StorageFailure.DataNotFound))
            withGetProtocolForUser(Either.Right(SupportedProtocol.MLS))
            withFetchMlsOneToOneConversation(Either.Right(CONVERSATION))
        }

        // when
        val result = useCase.invoke(USER_ID)

        // then
        assertIs<CreateConversationResult.Success>(result)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchMlsOneToOneConversation)
            .with(eq(USER_ID))
            .wasInvoked(exactly = once)
    }

    private fun arrange(block: Arrangement.() -> Unit) = Arrangement(block).arrange()

    internal class Arrangement(
        private val block: Arrangement.() -> Unit
    ) : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        OneOnOneProtocolSelectorArrangement by OneOnOneProtocolSelectorArrangementImpl(),
        ConversationGroupRepositoryArrangement by ConversationGroupRepositoryArrangementImpl() {

        fun arrange() = block().run {
            this@Arrangement to GetOrCreateOneToOneConversationUseCaseImpl(
                conversationRepository = conversationRepository,
                conversationGroupRepository = conversationGroupRepository,
                oneOnOneProtocolSelector = oneOnOneProtocolSelector
            )
        }
    }

    private companion object {
        val USER_ID = TestUser.USER_ID
        val MEMBER = listOf(USER_ID)
        val CONVERSATION = TestConversation.ONE_ON_ONE
    }
}
