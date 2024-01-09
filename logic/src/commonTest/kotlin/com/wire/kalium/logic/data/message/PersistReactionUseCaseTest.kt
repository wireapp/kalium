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

import com.wire.kalium.logic.data.message.reaction.ReactionRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

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
            date = "date"
        )

        verify(arrangement.reactionRepository)
            .suspendFunction(arrangement.reactionRepository::updateReaction)
            .with(any(), any(), any(), any(), eq("❤️"))
    }

    private class Arrangement {
        @Mock
        val reactionRepository = mock(ReactionRepository::class)

        init {
            given(reactionRepository)
                .suspendFunction(reactionRepository::updateReaction)
                .whenInvokedWith(any(), any(), any(), any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to PersistReactionUseCaseImpl(
            reactionRepository = reactionRepository
        )
    }
}
