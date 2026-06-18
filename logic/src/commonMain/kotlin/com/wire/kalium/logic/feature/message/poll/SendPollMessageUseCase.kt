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
import com.wire.kalium.common.functional.onFailure
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
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlin.uuid.Uuid

/**
 * Creates, persists, and sends a native poll message in a conversation.
 */
@Suppress("LongParameterList")
public class SendPollMessageUseCase internal constructor(
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
    public suspend operator fun invoke(
        conversationId: ConversationId,
        question: String,
        options: List<String>,
        allowMultipleAnswers: Boolean,
        hideVoterNames: Boolean
    ): MessageOperationResult = scope.async(dispatchers.io) {
        validate(question, options)?.let {
            return@async MessageOperationResult.Failure(it)
        }

        slowSyncRepository.slowSyncStatus.first { it is SlowSyncStatus.Complete }

        val generatedMessageUuid = Uuid.random().toString()
        val expectsReadConfirmation = userPropertyRepository.getReadReceiptsStatus()

        provideClientId().flatMap { clientId ->
            val content = MessageContent.Poll(
                question = question.trim(),
                options = options.map { option ->
                    MessageContent.Poll.Option(
                        id = Uuid.random().toString(),
                        text = option.trim()
                    )
                },
                allowMultipleAnswers = allowMultipleAnswers,
                hideVoterNames = hideVoterNames
            )

            val message = Message.Regular(
                id = generatedMessageUuid,
                content = content,
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
        }.fold(
            { MessageOperationResult.Failure(it) },
            { MessageOperationResult.Success }
        )
    }.await()

    private fun validate(question: String, options: List<String>): CoreFailure? {
        val trimmedOptions = options.map { it.trim() }
        return when {
            question.isBlank() -> invalid("Poll question cannot be blank")
            trimmedOptions.size < MIN_OPTIONS -> invalid("Poll requires at least $MIN_OPTIONS options")
            trimmedOptions.any { it.isBlank() } -> invalid("Poll options cannot be blank")
            else -> null
        }
    }

    private fun invalid(message: String) = CoreFailure.Unknown(IllegalArgumentException(message))

    internal companion object {
        internal const val TYPE = "Poll"
        private const val MIN_OPTIONS = 2
    }
}
