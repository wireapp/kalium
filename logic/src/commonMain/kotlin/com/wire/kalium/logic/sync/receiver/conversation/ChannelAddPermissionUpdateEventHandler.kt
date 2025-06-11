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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.util.createEventProcessingLogger
import io.mockative.Mockable

/**
 * Handles the [Event.Conversation.ConversationChannelAddPermission] event.
 */
@Mockable
interface ChannelAddPermissionUpdateEventHandler {
    suspend fun handle(event: Event.Conversation.ConversationChannelAddPermission): Either<CoreFailure, Unit>
}

internal class ChannelAddPermissionUpdateEventHandlerImpl(
    private val conversationRepository: ConversationRepository
) : ChannelAddPermissionUpdateEventHandler {
    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handle(event: Event.Conversation.ConversationChannelAddPermission): Either<CoreFailure, Unit> {
        val eventLogger = logger.createEventProcessingLogger(event)
        return conversationRepository.updateChannelAddPermissionLocally(event.conversationId, event.channelAddPermission)
            .onSuccess {
                eventLogger.logSuccess()
            }
            .onFailure {
                eventLogger.logFailure(it)
            }
    }
}
