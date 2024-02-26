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
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first

@Suppress("LongParameterList")
/**
 * @sample samples.logic.MessageUseCases.sendingBasicTextMessage
 * @sample samples.logic.MessageUseCases.sendingTextMessageWithMentions
 */
class SendButtonMessageUseCase internal constructor(
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: QualifiedID,
    private val provideClientId: CurrentClientIdProvider,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender,
    private val messageSendFailureHandler: MessageSendFailureHandler,
    private val userPropertyRepository: UserPropertyRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
    private val scope: CoroutineScope
) {

    suspend operator fun invoke(
        conversationId: ConversationId,
        text: String,
        mentions: List<MessageMention> = emptyList(),
        quotedMessageId: String? = null,
        buttons: List<String> = listOf()
    ): Either<CoreFailure, Unit> = scope.async(dispatchers.io) {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }

        val generatedMessageUuid = uuid4().toString()
        val expectsReadConfirmation = userPropertyRepository.getReadReceiptsStatus()

        provideClientId().flatMap { clientId ->
            val textContent = MessageContent.Text(
                value = text,
                mentions = mentions,
                quotedMessageReference = quotedMessageId?.let { quotedMessageId ->
                    MessageContent.QuoteReference(
                        quotedMessageId = quotedMessageId,
                        quotedMessageSha256 = null,
                        isVerified = true
                    )
                }
            )

            val transform: (String) -> MessageContent.Composite.Button = { MessageContent.Composite.Button(it, it, false) }
            val buttonContent = buttons.map(transform)
            val content = MessageContent.Composite(textContent, buttonContent)

            val message = Message.Regular(
                id = generatedMessageUuid,
                content = content,
                expectsReadConfirmation = expectsReadConfirmation,
                conversationId = conversationId,
                date = DateTimeUtil.currentIsoDateTimeString(),
                senderUserId = selfUserId,
                senderClientId = clientId,
                status = Message.Status.Pending,
                editStatus = Message.EditStatus.NotEdited,
                // According to proto Ephemeral it is not possible to send a Composite message with timer
                expirationData = null,
                isSelfMessage = true
            )
            persistMessage(message).flatMap {
                messageSender.sendMessage(message)
            }
        }.onFailure {
            messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
                failure = it,
                conversationId = conversationId,
                messageId = generatedMessageUuid,
                messageType = TYPE
            )
        }
    }.await()

    companion object {
        const val TYPE = "Text"
    }
}
