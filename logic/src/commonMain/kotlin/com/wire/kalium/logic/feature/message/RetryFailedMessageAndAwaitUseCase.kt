/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId

/**
 * Retries a persisted message and awaits its actual upload/send result and cleanup.
 * Work belongs to the caller. Cancellation preserves acknowledged sends and leaves unfinished messages retryable.
 */
public class RetryFailedMessageAndAwaitUseCase internal constructor(
    private val retryFailedMessage: RetryFailedMessageUseCase,
) {
    public suspend operator fun invoke(messageId: String, conversationId: ConversationId): MessageOperationResult =
        retryFailedMessage.invokeAndAwait(messageId, conversationId)
}
