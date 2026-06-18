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
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.CreateConversationParam
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageThreadRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.createconversation.ConversationCreationResult
import com.wire.kalium.logic.feature.conversation.createconversation.CreateRegularGroupUseCase

public sealed interface CreateConversationFromThreadResult {
    public data class Success(val conversation: Conversation) : CreateConversationFromThreadResult
    public data object NoThreadParticipants : CreateConversationFromThreadResult
    public data class ThreadReadFailure(val failure: StorageFailure) : CreateConversationFromThreadResult
    public data class ConversationCreationFailure(
        val failure: ConversationCreationResult,
    ) : CreateConversationFromThreadResult

    public data class MessageMoveFailure(
        val conversation: Conversation,
        val failure: StorageFailure,
    ) : CreateConversationFromThreadResult
}

public class CreateConversationFromThreadUseCase internal constructor(
    private val messageThreadRepository: MessageThreadRepository,
    private val createRegularGroup: CreateRegularGroupUseCase,
    private val selfUserId: UserId,
) {
    public suspend operator fun invoke(
        sourceConversationId: ConversationId,
        threadId: String,
        conversationName: String,
        options: CreateConversationParam = CreateConversationParam(),
    ): CreateConversationFromThreadResult {
        val participantIds = messageThreadRepository.getThreadParticipantIds(
            conversationId = sourceConversationId,
            threadId = threadId,
        ).fold(
            { return CreateConversationFromThreadResult.ThreadReadFailure(it) },
            { it.distinct().filterNot(selfUserId::equals) }
        )

        if (participantIds.isEmpty()) {
            return CreateConversationFromThreadResult.NoThreadParticipants
        }

        val creationResult = createRegularGroup(
            name = conversationName.ifBlank { DEFAULT_THREAD_CONVERSATION_NAME },
            userIdList = participantIds,
            options = options,
        )

        val newConversation = when (creationResult) {
            is ConversationCreationResult.Success -> creationResult.conversation
            else -> return CreateConversationFromThreadResult.ConversationCreationFailure(creationResult)
        }

        return messageThreadRepository.moveThreadMessagesToConversation(
            sourceConversationId = sourceConversationId,
            threadId = threadId,
            targetConversationId = newConversation.id,
        ).fold(
            {
                CreateConversationFromThreadResult.MessageMoveFailure(
                    conversation = newConversation,
                    failure = it,
                )
            },
            { CreateConversationFromThreadResult.Success(newConversation) }
        )
    }

    private companion object {
        const val DEFAULT_THREAD_CONVERSATION_NAME = "Thread"
    }
}
