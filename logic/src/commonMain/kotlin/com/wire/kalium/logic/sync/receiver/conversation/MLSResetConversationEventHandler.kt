/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.user.UserId

interface MLSResetConversationEventHandler {
    suspend fun handle(event: Event.Conversation.MLSReset)
}

internal class MLSResetConversationEventHandlerImpl(
    private val selfUserId: UserId,
    private val userConfig: UserConfigRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val fetchConversation: FetchConversationUseCase,
) : MLSResetConversationEventHandler {
    override suspend fun handle(event: Event.Conversation.MLSReset) {

        if (!userConfig.isMlsConversationsResetEnabled()) {
            kaliumLogger.i("MLS conversation reset feature is disabled.")
            return
        }

        if (event.from != selfUserId) {
            mlsConversationRepository.leaveGroup(event.groupID)

            // Will be replaced by updating Group ID when it is added in a new
            // version of mls-reset event.
            fetchConversation(event.conversationId)
        }
    }
}
