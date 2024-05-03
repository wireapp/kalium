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

package com.wire.kalium.logic.feature.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.util.DateTimeUtil

/**
 * This use case will clear all messages from a conversation and notify other clients, using the self conversation.
 */
interface ClearConversationContentUseCase {
    /**
     * @param conversationId The conversation id to clear all messages.
     * @return [Result] of the operation, indicating success or failure.
     */
    suspend operator fun invoke(conversationId: ConversationId): Result

    sealed class Result {
        data object Success : Result()
        data object Failure : Result()
    }
}

internal class ClearConversationContentUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val messageSender: MessageSender,
    private val selfUserId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfConversationIdProvider: SelfConversationIdProvider
) : ClearConversationContentUseCase {

    override suspend fun invoke(conversationId: ConversationId): ClearConversationContentUseCase.Result =
        conversationRepository.clearContent(conversationId).flatMap {
            currentClientIdProvider().flatMap { currentClientId ->
                selfConversationIdProvider().flatMap { selfConversationIds ->
                    selfConversationIds.foldToEitherWhileRight(Unit) { selfConversationId, _ ->
                        val regularMessage = Message.Signaling(
                            id = uuid4().toString(),
                            content = MessageContent.Cleared(
                                conversationId = conversationId,
                                time = DateTimeUtil.currentInstant()
                            ),
                            // sending the message to clear this conversation
                            conversationId = selfConversationId,
                            date = DateTimeUtil.currentIsoDateTimeString(),
                            senderUserId = selfUserId,
                            senderClientId = currentClientId,
                            status = Message.Status.Pending,
                            isSelfMessage = true,
                            expirationData = null
                        )
                        messageSender.sendMessage(regularMessage)
                    }
                }
            }
        }.fold({ ClearConversationContentUseCase.Result.Failure }, { ClearConversationContentUseCase.Result.Success })
}
