/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Suppress("LongParameterList")
/**
 * @sample samples.logic.MessageUseCases.sendingBasicTextMessage
 * @sample samples.logic.MessageUseCases.sendingTextMessageWithMentions
 */
class SendTextMessageUseCase internal constructor(
    private val messageRepository: MessageRepository,
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: QualifiedID,
    private val provideClientId: CurrentClientIdProvider,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender,
    private val userPropertyRepository: UserPropertyRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {

    suspend operator fun invoke(
        conversationId: ConversationId,
        text: String,
        mentions: List<MessageMention> = emptyList(),
        quotedMessageId: String? = null
    ): Either<CoreFailure, Unit> = withContext(dispatchers.io) {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }

        val generatedMessageUuid = uuid4().toString()
        val expectsReadConfirmation = userPropertyRepository.getReadReceiptsStatus()

        provideClientId().flatMap { clientId ->
            val message = Message.Regular(
                id = generatedMessageUuid,
                content = MessageContent.Text(
                    value = text,
                    mentions = mentions,
                    quotedMessageReference = quotedMessageId?.let { quotedMessageId ->
                        MessageContent.QuoteReference(
                            quotedMessageId = quotedMessageId,
                            quotedMessageSha256 = null,
                            isVerified = true
                        )
                    }
                ),
                expectsReadConfirmation = expectsReadConfirmation,
                conversationId = conversationId,
                date = DateTimeUtil.currentIsoDateTimeString(),
                senderUserId = selfUserId,
                senderClientId = clientId,
                status = Message.Status.PENDING,
                editStatus = Message.EditStatus.NotEdited,
                isSelfMessage = true
            )
            persistMessage(message)
                .flatMap {
                    messageSender.sendMessage(message)
                }
                .onSuccess {
                    messageRepository.updateMessageStatus(MessageEntity.Status.SENT, conversationId, generatedMessageUuid)
                }
        }.onFailure {
            when (it) {
                is NetworkFailure.FederatedBackendFailure -> {
                    kaliumLogger.i("Failed due to federation context availability.")
                    messageRepository.updateMessageStatus(MessageEntity.Status.FAILED_REMOTELY, conversationId, generatedMessageUuid)
                }
                else -> {
                    messageRepository.updateMessageStatus(MessageEntity.Status.FAILED, conversationId, generatedMessageUuid)
                }
            }
            if (it is CoreFailure.Unknown) {
                kaliumLogger.e("There was an unknown error trying to send the message $it", it.rootCause)
                it.rootCause?.printStackTrace()
            } else {
                kaliumLogger.e("There was an error trying to send the message $it")
            }
        }
    }
}
