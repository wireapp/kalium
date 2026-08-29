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

package com.wire.kalium.logic.data.message

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.IncomingReactionPersistence
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.ReactionEventData
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PersistReactionUseCaseTest {

    @Test
    fun givenSuccessfulUpdate_whenPersisting_thenPayloadIsPreservedHeartIsNormalizedAndHookIsNotified() = runTest {
        val persistence = RecordingIncomingReactionPersistence(Either.Right(Unit))
        val hookNotifier = RecordingPersistenceEventHookNotifier()
        val useCase = PersistReactionUseCaseImpl(persistence, selfUserId, hookNotifier)
        val reaction = MessageContent.Reaction(
            messageId = messageId,
            emojiSet = setOf("❤", "❤️", "👍"),
        )

        val result = useCase(reaction, conversationId, senderUserId, date)

        assertEquals(Either.Right(Unit), result)
        assertEquals(
            ReactionUpdate(messageId, conversationId, senderUserId, date, setOf("❤️", "👍")),
            persistence.calls.single(),
        )
        assertEquals(
            ReactionEventData(conversationId, messageId, date) to selfUserId,
            hookNotifier.reactionCalls.single(),
        )
    }

    @Test
    fun givenWrappedUpdateFailure_whenPersisting_thenFailureIsReturnedAndHookIsStillNotified() = runTest {
        val expectedException = IllegalStateException("reaction update failed")
        val expectedResult = Either.Left(StorageFailure.Generic(expectedException))
        val persistence = RecordingIncomingReactionPersistence(expectedResult)
        val hookNotifier = RecordingPersistenceEventHookNotifier()
        val useCase = PersistReactionUseCaseImpl(persistence, selfUserId, hookNotifier)

        val result = useCase(
            MessageContent.Reaction(messageId, setOf("👍")),
            conversationId,
            senderUserId,
            date,
        )

        assertEquals(expectedResult, result)
        assertEquals(
            ReactionEventData(conversationId, messageId, date) to selfUserId,
            hookNotifier.reactionCalls.single(),
        )
    }

    @Test
    fun givenUpdateCancellation_whenPersisting_thenCancellationEscapesAndHookIsNotNotified() = runTest {
        val expectedException = CancellationException("reaction update cancelled")
        val persistence = RecordingIncomingReactionPersistence(Either.Right(Unit), expectedException)
        val hookNotifier = RecordingPersistenceEventHookNotifier()
        val useCase = PersistReactionUseCaseImpl(persistence, selfUserId, hookNotifier)

        val actualException = assertFailsWith<CancellationException> {
            useCase(
                MessageContent.Reaction(messageId, setOf("👍")),
                conversationId,
                senderUserId,
                date,
            )
        }

        assertSame(expectedException, actualException)
        assertTrue(hookNotifier.reactionCalls.isEmpty())
    }

    private class RecordingIncomingReactionPersistence(
        private val result: Either<StorageFailure, Unit>,
        private val throwable: Throwable? = null,
    ) : IncomingReactionPersistence {
        val calls = mutableListOf<ReactionUpdate>()

        override suspend fun updateReaction(
            originalMessageId: String,
            conversationId: ConversationId,
            senderUserId: UserId,
            instant: Instant,
            userReactions: UserReactions,
        ): Either<StorageFailure, Unit> {
            calls += ReactionUpdate(originalMessageId, conversationId, senderUserId, instant, userReactions)
            throwable?.let { throw it }
            return result
        }
    }

    private class RecordingPersistenceEventHookNotifier : PersistenceEventHookNotifier {
        val reactionCalls = mutableListOf<Pair<ReactionEventData, UserId>>()

        override suspend fun onReactionPersisted(data: ReactionEventData, selfUserId: UserId) {
            reactionCalls += data to selfUserId
        }
    }

    private data class ReactionUpdate(
        val messageId: String,
        val conversationId: ConversationId,
        val senderUserId: UserId,
        val date: Instant,
        val reactions: UserReactions,
    )

    private companion object {
        const val messageId = "message-id"
        val conversationId = ConversationId("conversation-id", "wire.example")
        val senderUserId = UserId("sender-id", "wire.example")
        val selfUserId = UserId("self-id", "wire.example")
        val date = Instant.parse("2026-08-19T10:15:30Z")
    }
}
