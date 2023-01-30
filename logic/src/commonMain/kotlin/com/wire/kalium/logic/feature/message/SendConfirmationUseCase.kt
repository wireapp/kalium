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
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.util.DateTimeUtil

/**
 * This use case allows to send a confirmation type [ReceiptType.READ]
 *
 * - For 1:1 we take into consideration [UserPropertyRepository.getReadReceiptsStatus]
 * - For group conversations we have to look for each group conversation configuration.
 */
@Suppress("LongParameterList")
internal class SendConfirmationUseCase internal constructor(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val syncManager: SyncManager,
    private val messageSender: MessageSender,
    private val selfUserId: UserId,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val userPropertyRepository: UserPropertyRepository,
) {
    private companion object {
        const val TAG = "[SendConfirmationUseCase]"
    }

    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.MESSAGES) }

    suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit> {
        syncManager.waitUntilLive()

        val messageIds = getPendingUnreadMessagesIds(conversationId)
        if (messageIds.isEmpty()) {
            logger.d("$TAG skipping, NO messages to send confirmation signal")
            return Either.Right(Unit)
        }

        return currentClientIdProvider().flatMap { currentClientId ->
            val message = Message.Signaling(
                id = uuid4().toString(),
                content = MessageContent.Receipt(ReceiptType.READ, messageIds),
                conversationId = conversationId,
                date = DateTimeUtil.currentIsoDateTimeString(),
                senderUserId = selfUserId,
                senderClientId = currentClientId,
                status = Message.Status.PENDING,
            )

            messageSender.sendMessage(message)
        }.onFailure {
            logger.e("$TAG there was an error trying to send the confirmation signal $it")
        }.onSuccess {
            logger.d("$TAG confirmation signal sent successful")
        }
    }

    private suspend fun getPendingUnreadMessagesIds(conversationId: ConversationId): List<String> =
        if (isReceiptsEnabledForConversation(conversationId)) {
            messageRepository.getPendingConfirmationMessagesByConversationAfterDate(conversationId)
                .fold({
                    logger.e("$TAG There was an unknown error trying to get messages pending read confirmation $it")
                    emptyList()
                }, { it })
        } else emptyList()

    private suspend fun isReceiptsEnabledForConversation(conversationId: ConversationId) =
        conversationRepository.baseInfoById(conversationId).fold({
            false
        }, { conversation ->
            when (conversation.type) {
                Conversation.Type.ONE_ON_ONE -> userPropertyRepository.getReadReceiptsStatus()
                else -> conversation.receiptMode == Conversation.ReceiptMode.ENABLED
            }
        })
}
