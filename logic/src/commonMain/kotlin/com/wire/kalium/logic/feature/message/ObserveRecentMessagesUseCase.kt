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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observes recent messages from a conversation without waiting for slow sync to complete.
 * Reads directly from the local database, so cached messages are available immediately.
 *
 * Unlike [GetRecentMessagesUseCase], this does not gate on [SlowSyncStatus.Complete],
 * making it suitable for showing messages while sync is in progress.
 */
public class ObserveRecentMessagesUseCase internal constructor(
    private val messageRepository: MessageRepository,
) {

    /**
     * @param conversationId the id of the conversation
     * @param limit the number of messages to return for pagination
     * @param offset the offset of the messages to return for pagination
     * @param visibility the visibility of the messages to return @see [Message.Visibility]
     * @return the [Flow] of [List] of [Message] from the local database
     */
    public suspend operator fun invoke(
        conversationId: ConversationId,
        limit: Int = 100,
        offset: Int = 0,
        visibility: List<Message.Visibility> = Message.Visibility.entries
    ): Flow<List<Message>> {
        return messageRepository.getMessagesByConversationIdAndVisibility(conversationId, limit, offset, visibility)
    }
}
