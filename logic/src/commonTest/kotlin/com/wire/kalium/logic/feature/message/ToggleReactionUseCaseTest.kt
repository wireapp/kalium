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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.UserReactions
import com.wire.kalium.logic.data.message.reaction.ReactionRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.framework.stub.ReactionRepositoryStub
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ToggleReactionUseCaseTest {

    // FIXME: Mockative doesn't properly generate Mocks when there is a Typealias
    //       with generic types, such as typealias Foo = Bar<Thing>
    //       So ReactionRepository is mocked manually

    @Test
    fun givenReactionWasPreviouslyAdded_whenTogglingReaction_thenShouldRemoveItFromRepository() = runTest {
        val emojiReaction = "🫡"

        var deleteCallCount = 0
        val reactionRepository = object : ReactionRepositoryStub() {
            override suspend fun getSelfUserReactionsForMessage(
                originalMessageId: String,
                conversationId: ConversationId
            ): Either<StorageFailure, UserReactions> {
                return Either.Right(setOf(emojiReaction))
            }

            override suspend fun deleteReaction(
                originalMessageId: String,
                conversationId: ConversationId,
                senderUserId: UserId,
                emoji: String
            ): Either<StorageFailure, Unit> {
                deleteCallCount++
                assertEquals(TEST_MESSAGE_ID, originalMessageId)
                assertEquals(TEST_CONVERSATION_ID, conversationId)
                assertEquals(TEST_SELF_USER, senderUserId)
                assertEquals(emojiReaction, emoji)
                return Either.Right(Unit)
            }
        }

        val (_, toggleReactionUseCase) = Arrangement().arrange(reactionRepository)

        toggleReactionUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_ID, emojiReaction)

        assertEquals(1, deleteCallCount)
    }

    @Test
    fun givenReactionWasPreviouslyAdded_whenTogglingReaction_thenShouldSendAMessageWithRemovedReaction() = runTest {
        val emojiReaction = "🫡"

        val reactionRepository = object : ReactionRepositoryStub() {
            override suspend fun getSelfUserReactionsForMessage(
                originalMessageId: String,
                conversationId: ConversationId
            ): Either<StorageFailure, UserReactions> {
                return Either.Right(setOf(emojiReaction))
            }
        }

        val (arrangement, toggleReactionUseCase) = Arrangement().arrange(reactionRepository)

        toggleReactionUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_ID, emojiReaction)

        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(matching {
                val content = it.content as MessageContent.Reaction
                content.emojiSet.isEmpty() && content.messageId == TEST_MESSAGE_ID
            }, any())
    }

    @Test
    fun givenReactionWasNotPresent_whenTogglingReaction_thenShouldRemoveItFromRepository() = runTest {
        val emojiReaction = "🫡"

        var persistCallCount = 0
        val reactionRepository = object : ReactionRepositoryStub() {
            override suspend fun getSelfUserReactionsForMessage(
                originalMessageId: String,
                conversationId: ConversationId
            ): Either<StorageFailure, UserReactions> {
                return Either.Right(emptySet())
            }

            override suspend fun persistReaction(
                originalMessageId: String,
                conversationId: ConversationId,
                senderUserId: UserId,
                date: String,
                emoji: String
            ): Either<StorageFailure, Unit> {
                persistCallCount++
                assertEquals(TEST_MESSAGE_ID, originalMessageId)
                assertEquals(TEST_CONVERSATION_ID, conversationId)
                assertEquals(TEST_SELF_USER, senderUserId)
                assertEquals(emojiReaction, emoji)
                return Either.Right(Unit)
            }
        }

        val (_, toggleReactionUseCase) = Arrangement().arrange(reactionRepository)

        toggleReactionUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_ID, emojiReaction)

        assertEquals(1, persistCallCount)
    }

    @Test
    fun givenReactionWasNotPresent_whenTogglingReaction_thenShouldSendAMessageWithRemovedReaction() = runTest {
        val emojiReaction = "🫡"

        val reactionRepository = object : ReactionRepositoryStub() {
            override suspend fun getSelfUserReactionsForMessage(
                originalMessageId: String,
                conversationId: ConversationId
            ): Either<StorageFailure, UserReactions> {
                return Either.Right(setOf(emojiReaction))
            }
        }

        val (arrangement, toggleReactionUseCase) = Arrangement().arrange(reactionRepository)

        toggleReactionUseCase(TEST_CONVERSATION_ID, TEST_MESSAGE_ID, emojiReaction)

        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(matching {
                val content = it.content as MessageContent.Reaction
                content.emojiSet.size == 1 &&
                        content.emojiSet.first() == emojiReaction &&
                        content.messageId == TEST_MESSAGE_ID
            }, any())
    }

    private class Arrangement {

        val currentClientIdProvider: CurrentClientIdProvider = CurrentClientIdProvider { Either.Right(TEST_CURRENT_CLIENT) }

        @Mock
        val slowSyncRepository: SlowSyncRepository = mock(SlowSyncRepository::class)

        @Mock
        val messageSender: MessageSender = mock(MessageSender::class)

        init {
            withSlowSyncCompleted()
            withMessageSendingReturning(Either.Right(Unit))
        }

        fun withSlowSyncCompleted() = apply {
            given(slowSyncRepository)
                .getter(slowSyncRepository::slowSyncStatus)
                .whenInvoked()
                .thenReturn(MutableStateFlow(SlowSyncStatus.Complete))
        }

        fun withMessageSendingReturning(either: Either<CoreFailure, Unit>) = apply {
            given(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .whenInvokedWith(any(), any())
                .thenReturn(either)
        }

        fun arrange(reactionRepository: ReactionRepository) = this to ToggleReactionUseCase(
            currentClientIdProvider,
            TEST_SELF_USER,
            slowSyncRepository,
            reactionRepository,
            messageSender
        )

    }

    private companion object {
        val TEST_CURRENT_CLIENT = TestClient.CLIENT_ID
        val TEST_SELF_USER = TestUser.USER_ID
        const val TEST_MESSAGE_ID = "messageId"
        val TEST_CONVERSATION_ID = TestConversation.ID
    }
}
