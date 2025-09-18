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

import kotlin.uuid.Uuid
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mockable
import kotlinx.datetime.Clock

/**
 * This use case will clear all messages from a conversation and notify other clients, using the self conversation.
 */
@Mockable
interface ClearConversationContentUseCase {
    /**
     * @param conversationId The conversation id to clear all messages.
     * @return [Result] of the operation, indicating success or failure.
     */
    suspend operator fun invoke(conversationId: ConversationId, needToRemoveConversation: Boolean = false): Result

    sealed class Result {
        data object Success : Result()
        data class Failure(val failure: CoreFailure) : Result()
    }
}

internal class ClearConversationContentUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val messageSender: MessageSender,
    private val selfUserId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfConversationIdProvider: SelfConversationIdProvider,
    private val clearLocalConversationAssets: ClearConversationAssetsLocallyUseCase
) : ClearConversationContentUseCase {

    override suspend fun invoke(
        conversationId: ConversationId,
        needToRemoveConversation: Boolean
    ): ClearConversationContentUseCase.Result =
        currentClientIdProvider().flatMap { currentClientId ->
            selfConversationIdProvider().flatMap { selfConversationIds ->
                selfConversationIds.foldToEitherWhileRight(Unit) { selfConversationId, _ ->
                    val regularMessage = Message.Signaling(
                        id = Uuid.random().toString(),
                        content = MessageContent.Cleared(
                            conversationId = conversationId,
                            time = DateTimeUtil.currentInstant(),
                            needToRemoveLocally = needToRemoveConversation
                        ),
                        // sending the message to clear this conversation
                        conversationId = selfConversationId,
                        date = Clock.System.now(),
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
            .flatMap { clearLocalConversationAssets(conversationId) }
            .flatMap { conversationRepository.clearContent(conversationId) }
            .fold({ ClearConversationContentUseCase.Result.Failure(it) }, { ClearConversationContentUseCase.Result.Success })
}
