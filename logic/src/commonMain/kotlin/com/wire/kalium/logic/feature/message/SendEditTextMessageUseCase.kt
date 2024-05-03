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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly")

package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Suppress("LongParameterList")

/**
 * Edits a text message
 *
 * @sample samples.logic.MessageUseCases.sendingEditBasicTextMessage
 */
class SendEditTextMessageUseCase internal constructor(
    private val messageRepository: MessageRepository,
    private val selfUserId: QualifiedID,
    private val provideClientId: CurrentClientIdProvider,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender,
    private val messageSendFailureHandler: MessageSendFailureHandler,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {

    /**
     * Operation to edit a text message.
     *
     * @param conversationId the id of the conversation the message belongs to
     * @param originalMessageId the id of the message to edit
     * @param text the edited content of the message
     * @param mentions the edited mentions in the message
     * @return [Either] [CoreFailure] or [Unit] //fixme: we should not return [Either]
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        originalMessageId: String,
        text: String,
        mentions: List<MessageMention> = emptyList(),
        editedMessageId: String = uuid4().toString()
    ): Either<CoreFailure, Unit> = withContext(dispatchers.io) {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }

        provideClientId().flatMap { clientId ->
            val content = MessageContent.TextEdited(
                editMessageId = originalMessageId,
                newContent = text,
                newMentions = mentions
            )
            val message = Message.Signaling(
                id = editedMessageId,
                content = content,
                conversationId = conversationId,
                date = DateTimeUtil.currentIsoDateTimeString(),
                senderUserId = selfUserId,
                senderClientId = clientId,
                status = Message.Status.Pending,
                isSelfMessage = true,
                expirationData = null
            )
            // until the edit send is completed and accepted by the backend, we don't change the message id to be able to handle any
            // incoming edits from other clients that happened in the meantime and already changed the message id
            messageRepository.updateTextMessage(
                conversationId = message.conversationId,
                messageContent = content,
                newMessageId = originalMessageId,
                editTimeStamp = message.date
            ).flatMap {
                    messageRepository.updateMessageStatus(
                        messageStatus = MessageEntity.Status.PENDING,
                        conversationId = message.conversationId,
                        messageUuid = originalMessageId
                    )
                }
                .flatMap {
                    messageSender.sendMessage(message)
                }
        }.onFailure { messageSendFailureHandler.handleFailureAndUpdateMessageStatus(it, conversationId, originalMessageId, TYPE) }
    }

    companion object {
        const val TYPE = "TextEdited"
    }
}
