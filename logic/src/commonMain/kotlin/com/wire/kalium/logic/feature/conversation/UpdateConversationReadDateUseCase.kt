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
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.logic.feature.message.receipt.ConversationTimeEventInput
import com.wire.kalium.logic.feature.message.receipt.ConversationTimeEventWorker
import com.wire.kalium.logic.feature.message.receipt.ConversationWorkQueue
import com.wire.kalium.logic.feature.message.receipt.SendConfirmationUseCase
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.messaging.hooks.ConversationLastReadEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * This use case will update last read date for a conversation.
 * After that, will sync against other user's registered clients, using the self conversation.
 */
// todo(interface). extract interface for use case
// TODO: look into excluding self clients from sendConfirmation or run sendLastReadMessageToOtherClients if
//  the conversation does not need to be notified
@Suppress("LongParameterList")
public class UpdateConversationReadDateUseCase internal constructor(
    private val conversationRepository: ConversationRepository,
    private val messageSender: MessageSender,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfUserId: UserId,
    private val selfConversationIdProvider: SelfConversationIdProvider,
    private val sendConfirmation: SendConfirmationUseCase,
    private val workQueue: ConversationWorkQueue,
    private val persistenceEventHookNotifier: PersistenceEventHookNotifier,
    private val logger: KaliumLogger = kaliumLogger
) {

    /**
     * @param conversationId The conversation id to update the last read date.
     * @param time The last read date to update.
     * @param invokeImmediately If true, executes the work directly in the calling coroutine instead of
     *   enqueuing it to the debounced work queue. Use this when the read receipt must be sent within the
     *   active sync session (e.g. when replying from a notification).
     */
    public suspend operator fun invoke(
        conversationId: QualifiedID,
        time: Instant,
        invokeImmediately: Boolean = false,
    ) {
        if (invokeImmediately) {
            doWork(conversationId, time)
        } else {
            workQueue.enqueue(ConversationTimeEventInput(conversationId, time), worker)
        }
    }

    private val worker = ConversationTimeEventWorker { (conversationId, time) ->
        doWork(conversationId, time)
    }

    private suspend fun doWork(conversationId: QualifiedID, requestedLastRead: Instant) {
        coroutineScope {
            conversationRepository.observeConversationById(conversationId).first().onFailure {
                logger.w("Failed to update conversation read date; StorageFailure $it")
            }.onSuccess { conversation ->
                val storedLastRead = conversation.lastReadDate

                if (storedLastRead > requestedLastRead) {
                    logger.d(
                        "Skipping last-read update for '${conversationId.toLogString()}': " +
                            "stored=$storedLastRead > requested=$requestedLastRead"
                    )
                    return@onSuccess
                }

                val needsLocalPersistence = storedLastRead < requestedLastRead
                // Network operations must stay cancellable. NonCancellable execution is used only to finish local persistence.
                val canPerformRemoteSync = currentCoroutineContext()[kotlinx.coroutines.Job] != NonCancellable

                if (needsLocalPersistence) {
                    if (canPerformRemoteSync) {
                        launch {
                            sendReadConfirmations(conversationId, storedLastRead, requestedLastRead)
                        }
                    }
                    persistLastRead(conversationId, requestedLastRead)
                }

                if (canPerformRemoteSync) {
                    launch {
                        syncLastReadWithOtherClients(conversationId, requestedLastRead)
                    }
                }
            }
        }
    }

    private suspend fun sendReadConfirmations(
        conversationId: QualifiedID,
        previouslyReadUntil: Instant,
        newlyReadUntil: Instant,
    ) {
        sendConfirmation(conversationId, previouslyReadUntil, newlyReadUntil)
    }

    private suspend fun persistLastRead(conversationId: QualifiedID, time: Instant) {
        withContext(NonCancellable) {
            conversationRepository.updateConversationReadDate(conversationId, time).onSuccess {
                logger.d("Persisted last-read for '${conversationId.toLogString()}' at $time")
                persistenceEventHookNotifier.onConversationLastReadPersisted(
                    ConversationLastReadEventData(conversationId, time),
                    selfUserId
                )
            }
        }
    }

    private suspend fun syncLastReadWithOtherClients(conversationId: QualifiedID, lastRead: Instant) {
        selfConversationIdProvider().flatMap { selfConversationIds ->
            selfConversationIds.foldToEitherWhileRight(Unit) { selfConversationId, _ ->
                sendLastReadToSelfConversation(conversationId, selfConversationId, lastRead)
            }
        }
    }

    private suspend fun sendLastReadToSelfConversation(
        readConversationId: QualifiedID,
        selfConversationId: QualifiedID,
        lastRead: Instant
    ): Either<CoreFailure, Unit> {
        val generatedMessageUuid = Uuid.random().toString()

        return currentClientIdProvider().flatMap { currentClientId ->
            val regularMessage = Message.Signaling(
                id = generatedMessageUuid,
                content = MessageContent.LastRead(
                    messageId = generatedMessageUuid,
                    conversationId = readConversationId,
                    time = lastRead
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
