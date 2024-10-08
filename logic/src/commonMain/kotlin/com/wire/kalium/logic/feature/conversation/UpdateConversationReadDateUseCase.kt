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
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.feature.message.receipt.ConversationTimeEventInput
import com.wire.kalium.logic.feature.message.receipt.ConversationTimeEventWorker
import com.wire.kalium.logic.feature.message.receipt.ConversationWorkQueue
import com.wire.kalium.logic.feature.message.receipt.SendConfirmationUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * This use case will update last read date for a conversation.
 * After that, will sync against other user's registered clients, using the self conversation.
 */

// TODO: look into excluding self clients from sendConfirmation or run sendLastReadMessageToOtherClients if
//  the conversation does not need to be notified
@Suppress("LongParameterList")
class UpdateConversationReadDateUseCase internal constructor(
    private val conversationRepository: ConversationRepository,
    private val messageSender: MessageSender,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfUserId: UserId,
    private val selfConversationIdProvider: SelfConversationIdProvider,
    private val sendConfirmation: SendConfirmationUseCase,
    private val workQueue: ConversationWorkQueue,
    private val logger: KaliumLogger = kaliumLogger
) {

    /**
     * @param conversationId The conversation id to update the last read date.
     * @param time The last read date to update.
     */
    operator fun invoke(
        conversationId: QualifiedID,
        time: Instant,
        shouldWaitUntilLive: Boolean = true
    ) {
        workQueue.enqueue(
            ConversationTimeEventInput(conversationId, time, shouldWaitUntilLive),
            worker
        )
    }

    private val worker = ConversationTimeEventWorker { (conversationId, time, shouldWaitUntilLive) ->
        coroutineScope {
            conversationRepository.observeConversationById(conversationId).first().onFailure {
                logger.w("Failed to update conversation read date; StorageFailure $it")
            }.onSuccess { conversation ->
                if (conversation.lastReadDate >= time) {
                    // Skipping, as current lastRead is already newer than the scheduled one
                    return@onSuccess
                }
                launch {
                    sendConfirmation(conversationId, conversation.lastReadDate, time, shouldWaitUntilLive)
                }
                launch {
                    conversationRepository.updateConversationReadDate(conversationId, time)
                }
                launch {
                    selfConversationIdProvider().flatMap { selfConversationIds ->
                        selfConversationIds.foldToEitherWhileRight(Unit) { selfConversationId, _ ->
                            sendLastReadMessageToOtherClients(conversationId, selfConversationId, time)
                        }
                    }
                }
            }
        }
    }

    private suspend fun sendLastReadMessageToOtherClients(
        conversationId: QualifiedID,
        selfConversationId: QualifiedID,
        time: Instant
    ): Either<CoreFailure, Unit> {
        val generatedMessageUuid = uuid4().toString()

        return currentClientIdProvider().flatMap { currentClientId ->
            val regularMessage = Message.Signaling(
                id = generatedMessageUuid,
                content = MessageContent.LastRead(
                    messageId = generatedMessageUuid,
                    conversationId = conversationId,
                    time = time
                ),
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
