/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrFail
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.sending.MessageSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Marks a stored message and the following range unread, then synchronizes the user's other devices. */
public interface MarkConversationAsUnreadUseCase {
    public suspend operator fun invoke(conversationId: ConversationId, messageId: String): MarkConversationAsUnreadResult
}

public sealed interface MarkConversationAsUnreadResult {
    public data object Success : MarkConversationAsUnreadResult
    public data class Failure(val failure: CoreFailure) : MarkConversationAsUnreadResult
}

@Suppress("LongParameterList")
internal class MarkConversationAsUnreadUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val messageSender: MessageSender,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfUserId: UserId,
    private val selfConversationIdProvider: SelfConversationIdProvider,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) : MarkConversationAsUnreadUseCase {

    override suspend fun invoke(conversationId: ConversationId, messageId: String): MarkConversationAsUnreadResult =
        scope.async { markAndSync(conversationId, messageId) }.await()

    @Suppress("ReturnCount")
    private suspend fun markAndSync(conversationId: ConversationId, messageId: String): MarkConversationAsUnreadResult {
        val message = messageRepository.getMessageById(conversationId, messageId)
            .getOrFail { return MarkConversationAsUnreadResult.Failure(it) }
        val undeliveredStatuses = setOf(Message.Status.Pending, Message.Status.Failed, Message.Status.FailedRemotely)
        if (message !is Message.Regular || message.visibility != Message.Visibility.VISIBLE ||
            message.status in undeliveredStatuses
        ) {
            return MarkConversationAsUnreadResult.Failure(StorageFailure.DataNotFound)
        }

        val conversation = conversationRepository.getConversationById(conversationId)
            .getOrFail { return MarkConversationAsUnreadResult.Failure(it) }
        val now = Instant.fromEpochMilliseconds(clock.now().toEpochMilliseconds())
        val operationTime = maxOf(now, conversation.manualUnreadUpdatedAt?.plus(1.milliseconds) ?: now)
        if (message.date > operationTime || message.date.toEpochMilliseconds() < 0) {
            return MarkConversationAsUnreadResult.Failure(StorageFailure.DataNotFound)
        }
        conversationRepository.markConversationAsUnread(conversationId, message.date, operationTime)
            .getOrFail { return MarkConversationAsUnreadResult.Failure(it) }

        val clientId = currentClientIdProvider().getOrFail { return MarkConversationAsUnreadResult.Failure(it) }
        val selfConversationIds = selfConversationIdProvider().getOrFail { return MarkConversationAsUnreadResult.Failure(it) }
        var syncFailure: CoreFailure? = null
        for (selfConversationId in selfConversationIds) {
            val signaling = Message.Signaling(
                id = Uuid.random().toString(),
                content = MessageContent.MarkUnread(conversationId, message.date, operationTime),
                conversationId = selfConversationId,
                date = operationTime,
                senderUserId = selfUserId,
                senderClientId = clientId,
                status = Message.Status.Pending,
                isSelfMessage = true,
                expirationData = null,
            )
            val result = messageSender.sendMessage(signaling)
            if (result is Either.Left && syncFailure == null) syncFailure = result.value
        }
        return syncFailure?.let(MarkConversationAsUnreadResult::Failure) ?: MarkConversationAsUnreadResult.Success
    }
}
