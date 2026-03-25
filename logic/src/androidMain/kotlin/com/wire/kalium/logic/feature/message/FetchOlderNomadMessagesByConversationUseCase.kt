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

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

public class FetchOlderNomadMessagesByConversationUseCase internal constructor(
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl,
    private val messageRepository: MessageRepository,
) {

    /**
     * Fetches older messages for a given conversation in a remote data source and stores them in the local database.
     * This is typically used when the user scrolls up in the message list we want to load more messages from the past.
     */
    public suspend operator fun invoke(
        conversationId: ConversationId,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Unit = withContext(dispatcher.default) {
        messageRepository.extensions.fetchOlderNomadMessagesByConversationId(
            conversationId = conversationId,
            pageSize = pageSize,
        )
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }
}
