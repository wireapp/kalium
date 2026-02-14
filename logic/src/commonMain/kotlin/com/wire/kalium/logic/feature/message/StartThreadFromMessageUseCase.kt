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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.MessageThreadRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.datetime.Clock

public sealed interface StartThreadFromMessageResult {
    public data class Success(
        val threadId: String,
        val rootMessageId: String,
    ) : StartThreadFromMessageResult

    public data class Failure(val failure: CoreFailure) : StartThreadFromMessageResult
}

public class StartThreadFromMessageUseCase internal constructor(
    private val messageRepository: MessageRepository,
    private val messageThreadRepository: MessageThreadRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
    private val scope: CoroutineScope,
) {

    public suspend operator fun invoke(
        conversationId: ConversationId,
        rootMessageId: String,
    ): StartThreadFromMessageResult = scope.async(dispatchers.io) {
        messageThreadRepository.getThreadByRootMessage(conversationId, rootMessageId).fold(
            { failure ->
                if (failure is StorageFailure.DataNotFound) {
                    return@fold
                }
                kaliumLogger.e(
                    "Failed to read thread root mapping for conversationId=$conversationId rootMessageId=$rootMessageId. " +
                        "Failure = $failure"
                )
                return@async StartThreadFromMessageResult.Failure(failure)
            },
            { existingRoot ->
                if (existingRoot != null) {
                    return@async StartThreadFromMessageResult.Success(
                        threadId = existingRoot.threadId,
                        rootMessageId = existingRoot.rootMessageId,
                    )
                }
            }
        )

        val threadId = rootMessageId
        val now = Clock.System.now()
        val rootMessage = messageRepository.getMessageById(conversationId, rootMessageId).getOrNull()
        val rootCreationDate = rootMessage?.date ?: now
        val rootVisibility = (rootMessage as? Message.Standalone)?.visibility ?: Message.Visibility.VISIBLE

        messageThreadRepository.upsertThreadRoot(
            conversationId = conversationId,
            rootMessageId = rootMessageId,
            threadId = threadId,
            createdAt = now,
        ).fold(
            { failure ->
                kaliumLogger.e(
                    "Failed to persist thread root mapping for conversationId=$conversationId rootMessageId=$rootMessageId " +
                        "threadId=$threadId. Failure = $failure"
                )
                return@async StartThreadFromMessageResult.Failure(failure)
            },
            { /* no-op */ }
        )

        messageThreadRepository.upsertThreadItem(
            conversationId = conversationId,
            messageId = rootMessageId,
            threadId = threadId,
            isRoot = true,
            creationDate = rootCreationDate,
            visibility = rootVisibility,
        ).fold(
            { failure ->
                kaliumLogger.e(
                    "Failed to persist thread root item for conversationId=$conversationId rootMessageId=$rootMessageId " +
                        "threadId=$threadId. Failure = $failure"
                )
                return@async StartThreadFromMessageResult.Failure(failure)
            },
            { /* no-op */ }
        )

        StartThreadFromMessageResult.Success(
            threadId = threadId,
            rootMessageId = rootMessageId,
        )
    }.await()
}
