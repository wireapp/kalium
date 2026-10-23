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
import com.wire.kalium.messaging.sending.MessageSendFailureHandler
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.persistence.dao.message.MessageEntity
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Attempts to send pending regular messages created by this user.
 * Messages pending for at least seven days are marked as failed instead, allowing manual retry.
 * Age is measured from the last edit timestamp for edited messages, or creation time otherwise.
 * Expiry is checked only when this automatic retry runs.
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
    private val clock: Clock = Clock.System,
) : SendPendingMessagesUseCase {

    override suspend operator fun invoke(): SendPendingMessagesUseCase.Result {
        var result: SendPendingMessagesUseCase.Result = SendPendingMessagesUseCase.Result.Success

        messageRepository.getAllPendingMessagesFromUser(userId)
            .onSuccess { pendingMessages ->
                pendingMessages
                    .filterIsInstance<Message.Regular>()
                    .forEach { message ->
                        val pendingSince = (message.editStatus as? Message.EditStatus.Edited)?.lastEditInstant ?: message.date
                        if (clock.now() - pendingSince >= PENDING_MESSAGE_EXPIRY) {
                            messageRepository.updateMessageStatus(MessageEntity.Status.FAILED, message.conversationId, message.id)
                                .onFailure {
                                    kaliumLogger.withFeatureId(SYNC).w("Failed to expire pending message ${message.id}: $it")
                                    result = SendPendingMessagesUseCase.Result.Failure
                                }
                            return@forEach
                        }

                        when (message.content) {
                            is MessageContent.Asset -> sendPendingAssetMessage(message)
                            is MessageContent.Text if message.editStatus is Message.EditStatus.Edited ->
                                resendPendingTextEdit(message, message.content as MessageContent.Text)

                            is MessageContent.Location,
                            is MessageContent.Multipart,
                            is MessageContent.Poll,
                            is MessageContent.Text -> messageSender.sendPendingMessage(message.conversationId, message.id)

                            else -> {
                                val contentType = message.content::class.simpleName
                                kaliumLogger.withFeatureId(SYNC)
                                    .w("Skipping pending message ${message.id} with non-retryable content $contentType")
                            }
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
        private val PENDING_MESSAGE_EXPIRY = 7.days
        private const val TEXT_EDITED_TYPE = "TextEdited"
    }
}
