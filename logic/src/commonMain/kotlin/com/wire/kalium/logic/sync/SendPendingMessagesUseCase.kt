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

package com.wire.kalium.logic.sync

import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.EditMessageBuilder
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.messaging.sending.MessageSender

/**
 * Attempts to send all pending messages created by this user.
 */
public interface SendPendingMessagesUseCase {
    public suspend operator fun invoke(): Result

    public sealed interface Result {
        public data object Success : Result
        public data object Failure : Result
    }
}

internal class SendPendingMessagesUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val messageSender: MessageSender,
    private val userId: UserId,
    private val sendPendingAssetMessage: SendPendingAssetMessageUseCase,
    private val messageSendFailureHandler: MessageSendFailureHandler,
) : SendPendingMessagesUseCase {

    override suspend operator fun invoke(): SendPendingMessagesUseCase.Result {
        var result: SendPendingMessagesUseCase.Result = SendPendingMessagesUseCase.Result.Success

        messageRepository.getAllPendingMessagesFromUser(userId)
            .onSuccess { pendingMessages ->
                pendingMessages.forEach { message ->
                    when (message) {
                        is Message.Regular if message.content is MessageContent.Asset ->
                            sendPendingAssetMessage(message)

                        is Message.Regular if message.content is MessageContent.Text &&
                                message.editStatus is Message.EditStatus.Edited ->
                            resendPendingTextEdit(message, message.content as MessageContent.Text)

                        else -> messageSender.sendPendingMessage(message.conversationId, message.id)
                    }
                }
            }.onFailure {
                kaliumLogger.withFeatureId(SYNC).w("Failed to fetch and attempt retry of pending messages: $it")
                result = SendPendingMessagesUseCase.Result.Failure
            }

        return result
    }

    private suspend fun resendPendingTextEdit(
        originalMessage: Message.Regular,
        originalContent: MessageContent.Text,
    ) {
        val signaling = EditMessageBuilder.buildTextEditSignaling(originalMessage, originalContent)
        messageSender.sendMessage(signaling)
            .onFailure { failure ->
                kaliumLogger.withFeatureId(SYNC)
                    .i("Failed to resend pending text edit for message ${originalMessage.id}. Failure = $failure")
                messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
                    failure = failure,
                    conversationId = originalMessage.conversationId,
                    messageId = originalMessage.id,
                    messageType = TEXT_EDITED_TYPE,
                    scheduleResendIfNoNetwork = false,
                )
            }
    }

    companion object {
        private const val TEXT_EDITED_TYPE = "TextEdited"
    }
}
