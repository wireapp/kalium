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
package com.wire.kalium.logic.feature.message.ephemeral

import com.wire.kalium.logic.data.id.ConversationId

/**
 * This use case enqueue the self deletion of a [Message] for a specific conversation id and message id
 */
interface EnqueueMessageSelfDeletionUseCase {
    operator fun invoke(conversationId: ConversationId, messageId: String)
}

internal class EnqueueMessageSelfDeletionUseCaseImpl(
    private val ephemeralMessageDeletionHandler: EphemeralMessageDeletionHandler
) : EnqueueMessageSelfDeletionUseCase {
    override operator fun invoke(conversationId: ConversationId, messageId: String) {
        ephemeralMessageDeletionHandler.startSelfDeletion(
            conversationId = conversationId,
            messageId = messageId
        )
    }
}
