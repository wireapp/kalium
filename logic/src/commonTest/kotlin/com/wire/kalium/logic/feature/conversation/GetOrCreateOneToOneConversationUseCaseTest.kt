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
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class GetOrCreateOneToOneConversationUseCaseTest {

    @Test
    fun givenConversationExist_whenCallingTheUseCase_ThenDoNotCreateAConversationButReturnExisting() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withObserveOneToOneConversationWithOtherUserReturning(Either.Right(CONVERSATION))
            .withGetDefaultProtocolReturning(Either.Right(SupportedProtocol.PROTEUS))
            .arrange()

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
    fun givenConversationDoesNotExist_whenCallingTheUseCase_ThenCreateAConversationAndReturn() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withObserveOneToOneConversationWithOtherUserReturning(Either.Left(StorageFailure.DataNotFound))
            .withGetDefaultProtocolReturning(Either.Right(SupportedProtocol.PROTEUS))
            .withCreateGroupConversationReturning(Either.Right(CONVERSATION))
            .arrange()

        // when
        val result = useCase.invoke(USER_ID)

        // then
        assertIs<CreateConversationResult.Success>(result)

        verify(arrangement.conversationGroupRepository)
            .suspendFunction(arrangement.conversationGroupRepository::createGroupConversation)
            .with(eq(null), eq(MEMBER), eq(ConversationOptions()))
            .wasInvoked(exactly = once)
    }

    class Arrangement {

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val conversationGroupRepository = mock(classOf<ConversationGroupRepository>())

        @Mock
        val establishMLSOneToOne = mock(classOf<EstablishMLSOneToOneUseCase>())

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        fun withObserveOneToOneConversationWithOtherUserReturning(result: Either<CoreFailure, Conversation>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::observeOneToOneConversationWithOtherUser)
                .whenInvokedWith(eq(USER_ID))
                .thenReturn(flowOf(result))
        }

        fun withCreateGroupConversationReturning(result: Either<CoreFailure, Conversation>) = apply {
            given(conversationGroupRepository)
                .suspendFunction(conversationGroupRepository::createGroupConversation)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(result)
        }

        fun withGetDefaultProtocolReturning(result: Either<StorageFailure, SupportedProtocol>) = apply {
            given(userConfigRepository)
                .function(userConfigRepository::getDefaultProtocol)
                .whenInvoked()
                .thenReturn(result)
        }

        fun arrange() = this to GetOrCreateOneToOneConversationUseCase(
            conversationRepository = conversationRepository,
            conversationGroupRepository = conversationGroupRepository,
            establishMLSOneToOne = establishMLSOneToOne,
            userConfigRepository = userConfigRepository
        )
    }

    private companion object {
        val USER_ID = TestUser.USER_ID
        val MEMBER = listOf(USER_ID)
        val CONVERSATION = TestConversation.ONE_ON_ONE
    }
}
