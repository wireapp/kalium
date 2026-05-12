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
import com.wire.kalium.logic.data.message.reaction.ReactionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.messaging.hooks.ConversationLastReadEventData
import com.wire.kalium.messaging.hooks.NoOpPersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.ReactionEventData
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

class PersistReactionUseCaseTest {

    @Test
    fun givenHeavyBlackHeartInReactions_whenPersisting_thenShouldConvertToHeartEmoji() = runTest {
        val (arrangement, persistReactionUseCase) = Arrangement().arrange()

        persistReactionUseCase(
            reaction = MessageContent.Reaction(
                messageId = "messageId",
                emojiSet = setOf("❤")
            ),
            conversationId = TestConversation.ID,
            senderUserId = TestUser.USER_ID,
            date = Instant.DISTANT_PAST
        )

        verifySuspend {
            arrangement.reactionRepository.updateReaction(any(), any(), any(), any(), eq(setOf("❤️")))
        }
    }

    @Test
    fun givenReactionPersistedSuccessfully_whenPersisting_thenHookIsNotified() = runTest {
        val hookNotifier = RecordingPersistenceEventHookNotifier()
        val (_, persistReactionUseCase) = Arrangement(hookNotifier = hookNotifier).arrange()

        persistReactionUseCase(
            reaction = MessageContent.Reaction(
                messageId = "messageId",
                emojiSet = setOf("👍")
            ),
            conversationId = TestConversation.ID,
            senderUserId = TestUser.USER_ID,
            date = Instant.DISTANT_PAST
        )

        assertEquals(1, hookNotifier.reactionCalls.size)
        val (data, selfUserId) = hookNotifier.reactionCalls.single()
        assertEquals(TestConversation.ID, data.conversationId)
        assertEquals("messageId", data.messageId)
        assertEquals(Instant.DISTANT_PAST, data.date)
        assertEquals(TestUser.USER_ID, selfUserId)
    }

    @Test
    fun givenReactionPersistFails_whenPersisting_thenHookIsStillNotified() = runTest {
        val hookNotifier = RecordingPersistenceEventHookNotifier()
        val (_, persistReactionUseCase) = Arrangement(hookNotifier = hookNotifier).arrangeWithFailure()

        persistReactionUseCase(
            reaction = MessageContent.Reaction(
                messageId = "messageId",
                emojiSet = setOf("👍")
            ),
            conversationId = TestConversation.ID,
            senderUserId = TestUser.USER_ID,
            date = Instant.DISTANT_PAST
        )

        assertEquals(1, hookNotifier.reactionCalls.size)
        val (data, selfUserId) = hookNotifier.reactionCalls.single()
        assertEquals(TestConversation.ID, data.conversationId)
        assertEquals("messageId", data.messageId)
        assertEquals(TestUser.USER_ID, selfUserId)
    }

    private class Arrangement(
        private val hookNotifier: PersistenceEventHookNotifier = NoOpPersistenceEventHookNotifier
    ) {

        val reactionRepository = mock<ReactionRepository>(mode = MockMode.autoUnit)

        suspend fun arrange() = this to PersistReactionUseCaseImpl(
            reactionRepository = reactionRepository,
            selfUserId = TestUser.USER_ID,
            persistenceEventHookNotifier = hookNotifier,
        ).also {
            everySuspend {
                reactionRepository.updateReaction(any(), any(), any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun arrangeWithFailure() = this to PersistReactionUseCaseImpl(
            reactionRepository = reactionRepository,
            selfUserId = TestUser.USER_ID,
            persistenceEventHookNotifier = hookNotifier,
        ).also {
            everySuspend {
                reactionRepository.updateReaction(any(), any(), any(), any(), any())
            }.returns(Either.Left(StorageFailure.DataNotFound))
        }
    }

    private class RecordingPersistenceEventHookNotifier : PersistenceEventHookNotifier {
        val reactionCalls = mutableListOf<Pair<ReactionEventData, UserId>>()

        override suspend fun onReactionPersisted(data: ReactionEventData, selfUserId: UserId) {
            reactionCalls += data to selfUserId
        }

        override suspend fun onConversationLastReadPersisted(
            data: ConversationLastReadEventData,
            selfUserId: UserId
        ) = Unit
    }
}
