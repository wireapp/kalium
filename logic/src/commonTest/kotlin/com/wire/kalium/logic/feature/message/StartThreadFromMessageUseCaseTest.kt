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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.MessageThreadRepository
import com.wire.kalium.logic.data.message.MessageThreadRoot
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StartThreadFromMessageUseCaseTest {

    @Test
    fun givenRootAlreadyMapped_whenStartingThread_thenReturnsExistingWithoutNewWrites() = runTest {
        val arrangement = Arrangement()
            .withExistingThread()
            .arrange(scope = this)

        val result = arrangement.useCase(TEST_CONVERSATION_ID, TEST_ROOT_MESSAGE_ID)

        assertIs<StartThreadFromMessageResult.Success>(result)
        assertEquals(TEST_EXISTING_THREAD_ID, result.threadId)
        assertEquals(TEST_ROOT_MESSAGE_ID, result.rootMessageId)
        coVerify { arrangement.messageThreadRepository.upsertThreadRoot(any(), any(), any(), any()) }.wasNotInvoked()
        coVerify { arrangement.messageThreadRepository.upsertThreadItem(any(), any(), any(), any(), any(), any()) }.wasNotInvoked()
    }

    @Test
    fun givenNoMapping_whenStartingThread_thenCreatesDeterministicThreadIdEqualToRootMessageId() = runTest {
        val arrangement = Arrangement()
            .withNoExistingThread()
            .withPersistSuccess()
            .arrange(scope = this)

        val result = arrangement.useCase(TEST_CONVERSATION_ID, TEST_ROOT_MESSAGE_ID)

        assertIs<StartThreadFromMessageResult.Success>(result)
        assertEquals(TEST_ROOT_MESSAGE_ID, result.threadId)
        assertEquals(TEST_ROOT_MESSAGE_ID, result.rootMessageId)
        coVerify { arrangement.messageThreadRepository.upsertThreadRoot(any(), any(), any(), any()) }.wasInvoked(once)
        coVerify { arrangement.messageThreadRepository.upsertThreadItem(any(), any(), any(), any(), any(), any()) }.wasInvoked(once)
    }

    @Test
    fun givenLookupReturnsDataNotFound_whenStartingThread_thenTreatAsMissingAndCreateThread() = runTest {
        val arrangement = Arrangement()
            .withThreadLookupDataNotFound()
            .withPersistSuccess()
            .arrange(scope = this)

        val result = arrangement.useCase(TEST_CONVERSATION_ID, TEST_ROOT_MESSAGE_ID)

        assertIs<StartThreadFromMessageResult.Success>(result)
        assertEquals(TEST_ROOT_MESSAGE_ID, result.threadId)
        coVerify { arrangement.messageThreadRepository.upsertThreadRoot(any(), any(), any(), any()) }.wasInvoked(once)
        coVerify { arrangement.messageThreadRepository.upsertThreadItem(any(), any(), any(), any(), any(), any()) }.wasInvoked(once)
    }

    @Test
    fun givenRootWriteFails_whenStartingThread_thenReturnsFailure() = runTest {
        val arrangement = Arrangement()
            .withNoExistingThread()
            .withRootWriteFailure()
            .arrange(scope = this)

        val result = arrangement.useCase(TEST_CONVERSATION_ID, TEST_ROOT_MESSAGE_ID)

        assertIs<StartThreadFromMessageResult.Failure>(result)
        coVerify { arrangement.messageThreadRepository.upsertThreadItem(any(), any(), any(), any(), any(), any()) }.wasNotInvoked()
    }

    private class Arrangement {
        val messageRepository = mock(MessageRepository::class)
        val messageThreadRepository = mock(MessageThreadRepository::class)
        lateinit var useCase: StartThreadFromMessageUseCase

        suspend fun withExistingThread() = apply {
            coEvery {
                messageThreadRepository.getThreadByRootMessage(TEST_CONVERSATION_ID, TEST_ROOT_MESSAGE_ID)
            }.returns(
                Either.Right(
                    MessageThreadRoot(
                        conversationId = TEST_CONVERSATION_ID,
                        rootMessageId = TEST_ROOT_MESSAGE_ID,
                        threadId = TEST_EXISTING_THREAD_ID,
                        createdAt = Instant.DISTANT_PAST
                    )
                )
            )
        }

        suspend fun withNoExistingThread() = apply {
            coEvery {
                messageThreadRepository.getThreadByRootMessage(TEST_CONVERSATION_ID, TEST_ROOT_MESSAGE_ID)
            }.returns(Either.Right(null))
        }

        suspend fun withPersistSuccess() = apply {
            coEvery { messageRepository.getMessageById(any(), any()) }.returns(Either.Left(StorageFailure.DataNotFound))
            coEvery { messageThreadRepository.upsertThreadRoot(any(), any(), any(), any()) }.returns(Either.Right(Unit))
            coEvery { messageThreadRepository.upsertThreadItem(any(), any(), any(), any(), any(), any()) }.returns(Either.Right(Unit))
        }

        suspend fun withThreadLookupDataNotFound() = apply {
            coEvery {
                messageThreadRepository.getThreadByRootMessage(TEST_CONVERSATION_ID, TEST_ROOT_MESSAGE_ID)
            }.returns(Either.Left(StorageFailure.DataNotFound))
        }

        suspend fun withRootWriteFailure() = apply {
            coEvery { messageRepository.getMessageById(any(), any()) }.returns(Either.Left(StorageFailure.DataNotFound))
            coEvery { messageThreadRepository.upsertThreadRoot(any(), any(), any(), any()) }
                .returns(Either.Left(StorageFailure.DataNotFound))
        }

        fun arrange(scope: kotlinx.coroutines.CoroutineScope): Arrangement = apply {
            useCase = StartThreadFromMessageUseCase(
                messageRepository = messageRepository,
                messageThreadRepository = messageThreadRepository,
                dispatchers = scope.testKaliumDispatcher,
                scope = scope
            )
        }
    }

    private companion object {
        val TEST_CONVERSATION_ID = ConversationId("conversation-id", "wire.com")
        const val TEST_ROOT_MESSAGE_ID = "root-message-id"
        const val TEST_EXISTING_THREAD_ID = "existing-thread-id"
    }
}
