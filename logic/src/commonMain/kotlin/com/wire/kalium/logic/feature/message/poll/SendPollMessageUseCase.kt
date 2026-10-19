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
package com.wire.kalium.logic.feature.message.poll

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.message.MessageOperationResult
import com.wire.kalium.messaging.sending.MessageSendFailureHandler
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Creates, persists, and sends a native end-to-end encrypted poll.
 * Polls are never self-deleting, because the ephemeral message format cannot carry them.
 * Sending runs in the session scope, so it completes even if the caller is cancelled.
 */
@Suppress("LongParameterList")
public class SendPollMessageUseCase internal constructor(
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: QualifiedID,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender,
    private val messageSendFailureHandler: MessageSendFailureHandler,
    private val userPropertyRepository: UserPropertyRepository,
    private val pendingMessagesEnabled: Boolean = true,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl,
    private val scope: CoroutineScope,
) {
    /**
     * @param question the poll question; surrounding whitespace is removed
     * @param options between [MIN_OPTIONS] and [MAX_OPTIONS] distinct, non-blank answers, in display order
     */
    public suspend operator fun invoke(
        conversationId: ConversationId,
        question: String,
        options: List<String>,
        allowMultipleAnswers: Boolean
    ): MessageOperationResult = scope.async(dispatcher.io) {
        val trimmedQuestion = question.trim()
        val trimmedOptions = options.map { it.trim() }
        if (!isValid(trimmedQuestion, trimmedOptions)) {
            return@async MessageOperationResult.Failure(
                CoreFailure.Unknown(IllegalArgumentException("Invalid poll question or options"))
            )
        }

        slowSyncRepository.slowSyncStatus.first { it is SlowSyncStatus.Complete }

        val messageId = Uuid.random().toString()
        val expectsReadConfirmation = userPropertyRepository.getReadReceiptsStatus()

        currentClientIdProvider().flatMap { clientId ->
            val message = Message.Regular(
                id = messageId,
                content = MessageContent.Poll(
                    question = trimmedQuestion,
                    options = trimmedOptions.map { MessageContent.Poll.Option(id = Uuid.random().toString(), text = it) },
                    allowMultipleAnswers = allowMultipleAnswers
                ),
                expectsReadConfirmation = expectsReadConfirmation,
                conversationId = conversationId,
                date = Clock.System.now(),
                senderUserId = selfUserId,
                senderClientId = clientId,
                status = Message.Status.Pending,
                editStatus = Message.EditStatus.NotEdited,
                expirationData = null,
                isSelfMessage = true
            )
            persistMessage(message).flatMap { messageSender.sendMessage(message) }
        }.fold(
            {
                messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
                    failure = it,
                    conversationId = conversationId,
                    messageId = messageId,
                    messageType = TYPE,
                    scheduleResendIfNoNetwork = pendingMessagesEnabled,
                )
                MessageOperationResult.Failure(it)
            },
            { MessageOperationResult.Success }
        )
    }.await()

    private fun isValid(question: String, options: List<String>): Boolean =
        question.isNotEmpty() &&
                options.size in MIN_OPTIONS..MAX_OPTIONS &&
                options.none { it.isEmpty() } &&
                options.distinctBy { it.lowercase() }.size == options.size

    public companion object {
        public const val MIN_OPTIONS: Int = 2
        public const val MAX_OPTIONS: Int = 10
        internal const val TYPE = "Poll"
    }
}
