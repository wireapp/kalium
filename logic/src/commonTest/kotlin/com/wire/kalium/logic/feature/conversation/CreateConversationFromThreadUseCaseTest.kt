/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.CreateConversationParam
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageThreadRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.createconversation.ConversationCreationResult
import com.wire.kalium.logic.feature.conversation.createconversation.CreateRegularGroupUseCase
import com.wire.kalium.logic.framework.TestConversation
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CreateConversationFromThreadUseCaseTest {

    @Test
    fun givenThreadParticipants_whenCreatingConversation_thenCreatesGroupWithoutSelfAndMovesMessages() = runTest {
        val arrangement = Arrangement()
            .withThreadParticipants(listOf(SELF_USER_ID, OTHER_USER_ID, OTHER_USER_ID_2))
            .withConversationCreated()
            .withMoveSuccess()
            .arrange()

        val result = arrangement.useCase(SOURCE_CONVERSATION_ID, THREAD_ID, CONVERSATION_NAME)

        assertIs<CreateConversationFromThreadResult.Success>(result)
        assertEquals(TestConversation.ID, result.conversation.id)
        assertEquals(listOf(OTHER_USER_ID, OTHER_USER_ID_2), arrangement.createRegularGroup.userIdList)
        coVerify {
            arrangement.messageThreadRepository.moveThreadMessagesToConversation(any(), any(), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenOnlySelfInteractedWithThread_whenCreatingConversation_thenReturnsNoThreadParticipants() = runTest {
        val arrangement = Arrangement()
            .withThreadParticipants(listOf(SELF_USER_ID))
            .arrange()

        val result = arrangement.useCase(SOURCE_CONVERSATION_ID, THREAD_ID, CONVERSATION_NAME)

        assertIs<CreateConversationFromThreadResult.NoThreadParticipants>(result)
        assertEquals(null, arrangement.createRegularGroup.userIdList)
    }

    @Test
    fun givenConversationCreationFails_whenCreatingConversation_thenDoesNotMoveMessages() = runTest {
        val arrangement = Arrangement()
            .withThreadParticipants(listOf(OTHER_USER_ID))
            .withConversationCreationFailure()
            .arrange()

        val result = arrangement.useCase(SOURCE_CONVERSATION_ID, THREAD_ID, CONVERSATION_NAME)

        assertIs<CreateConversationFromThreadResult.ConversationCreationFailure>(result)
        coVerify {
            arrangement.messageThreadRepository.moveThreadMessagesToConversation(any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenMessagesMoveFails_whenCreatingConversation_thenReturnsMoveFailureWithCreatedConversation() = runTest {
        val arrangement = Arrangement()
            .withThreadParticipants(listOf(OTHER_USER_ID))
            .withConversationCreated()
            .withMoveFailure()
            .arrange()

        val result = arrangement.useCase(SOURCE_CONVERSATION_ID, THREAD_ID, CONVERSATION_NAME)

        assertIs<CreateConversationFromThreadResult.MessageMoveFailure>(result)
        assertEquals(TestConversation.ID, result.conversation.id)
        assertIs<StorageFailure.DataNotFound>(result.failure)
    }

    private class Arrangement {
        val messageThreadRepository = mock(MessageThreadRepository::class)
        val createRegularGroup = FakeCreateRegularGroup()

        val useCase = CreateConversationFromThreadUseCase(
            messageThreadRepository = messageThreadRepository,
            createRegularGroup = createRegularGroup,
            selfUserId = SELF_USER_ID,
        )

        suspend fun withThreadParticipants(participantIds: List<UserId>) = apply {
            coEvery {
                messageThreadRepository.getThreadParticipantIds(SOURCE_CONVERSATION_ID, THREAD_ID)
            }.returns(Either.Right(participantIds))
        }

        fun withConversationCreated() = apply {
            createRegularGroup.result = ConversationCreationResult.Success(TestConversation.GROUP())
        }

        fun withConversationCreationFailure() = apply {
            createRegularGroup.result = ConversationCreationResult.SyncFailure
        }

        suspend fun withMoveSuccess() = apply {
            coEvery {
                messageThreadRepository.moveThreadMessagesToConversation(SOURCE_CONVERSATION_ID, THREAD_ID, TestConversation.ID)
            }.returns(Either.Right(Unit))
        }

        suspend fun withMoveFailure() = apply {
            coEvery {
                messageThreadRepository.moveThreadMessagesToConversation(SOURCE_CONVERSATION_ID, THREAD_ID, TestConversation.ID)
            }.returns(Either.Left(StorageFailure.DataNotFound))
        }

        fun arrange(): Arrangement = this
    }

    private class FakeCreateRegularGroup : CreateRegularGroupUseCase {
        var userIdList: List<UserId>? = null
        var result: ConversationCreationResult = ConversationCreationResult.SyncFailure

        override suspend fun invoke(
            name: String,
            userIdList: List<UserId>,
            options: CreateConversationParam,
        ): ConversationCreationResult {
            this.userIdList = userIdList
            return result
        }
    }

    private companion object {
        val SOURCE_CONVERSATION_ID = ConversationId("source", "wire.com")
        val SELF_USER_ID = UserId("self", "wire.com")
        val OTHER_USER_ID = UserId("other", "wire.com")
        val OTHER_USER_ID_2 = UserId("other-2", "wire.com")
        const val THREAD_ID = "thread-id"
        const val CONVERSATION_NAME = "Thread: Hello"
    }
}
