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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.reaction.ReactionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either

interface PersistReactionUseCase {
    suspend operator fun invoke(
        reaction: MessageContent.Reaction,
        conversationId: ConversationId,
        senderUserId: UserId,
        date: String
    ): Either<CoreFailure, Unit>
}

internal class PersistReactionUseCaseImpl(
    private val reactionRepository: ReactionRepository,
) : PersistReactionUseCase {
    override suspend operator fun invoke(
        reaction: MessageContent.Reaction,
        conversationId: ConversationId,
        senderUserId: UserId,
        date: String
    ): Either<CoreFailure, Unit> {
        val emojiSet = reaction.emojiSet.map {
            // If we receive the heavy black heart unicode, we convert it to the emoji version.
            // This is a compatibility layer, so we properly handle reactions sent from older clients
            // This does not cover the fact that we send the new emoji to older clients.
            if (it == "❤") { // \u2764
                "❤️" // \u2764\ufe0f (heavy black heart + variation selector 16)
            } else {
                it
            }
        }.toSet()
        return reactionRepository.updateReaction(
            reaction.messageId,
            conversationId,
            senderUserId,
            date,
            emojiSet
        )
    }
}
