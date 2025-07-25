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
package com.wire.kalium.logic.feature.message.composite

import com.benasher44.uuid.uuid4
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.CompositeMessageRepository
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageMetadataRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.datetime.Clock

class SendButtonActionMessageUseCase internal constructor(
    private val messageSender: MessageSender,
    private val compositeMessageRepository: CompositeMessageRepository,
    private val messageMetadataRepository: MessageMetadataRepository,
    private val syncManager: SyncManager,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfUserId: UserId
) {
    /**
     * Use case for sending a button action message, this means pressing a button in a [MessageContent.Composite].
     * Sends a button action message.
     *
     * @param conversationId The conversation id.
     * @param messageId The id of the message that contains the button.
     * @param buttonId The id of the button.
     * @return [Result.Success] if the message was sent successfully, [Result.Failure] otherwise.
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        messageId: String,
        buttonId: String
    ): Result = syncManager.waitUntilLiveOrFailure().flatMap {
        messageMetadataRepository.originalSenderId(
            conversationId,
            messageId
        ).flatMap { _ ->
            currentClientIdProvider().flatMap { currentClientId ->
                val regularMessage = Message.Signaling(
                    id = uuid4().toString(),
                    content = MessageContent.ButtonAction(
                        referencedMessageId = messageId,
                        buttonId = buttonId
                    ),
                    conversationId = conversationId,
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
    }.flatMap {
        compositeMessageRepository.markSelected(
            messageId = messageId,
            conversationId = conversationId,
            buttonId = buttonId
        )
    }.fold(Result::Failure) {
        Result.Success
    }

    sealed interface Result {
        data object Success : Result
        data class Failure(
            val error: CoreFailure
        ) : Result
    }
}
