/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.toDao
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventLoggingStatus
import com.wire.kalium.logic.data.event.logEventProcessing
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.SystemMessageBuilder
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.ConversationDAO

interface ProtocolUpdateEventHandler {
    suspend fun handle(event: Event.Conversation.ConversationProtocol)
}

internal class ProtocolUpdateEventHandlerImpl(
    private val conversationDAO: ConversationDAO,
    private val systemMessageBuilder: SystemMessageBuilder
) : ProtocolUpdateEventHandler {

    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handle(event: Event.Conversation.ConversationProtocol) {
        updateProtocol(event)
            .onSuccess { updated ->
                if (updated) {
                    systemMessageBuilder.insertProtocolChangedSystemMessage(event)
                }
                logger
                    .logEventProcessing(
                        EventLoggingStatus.SUCCESS,
                        event
                    )
            }
            .onFailure { coreFailure ->
                logger
                    .logEventProcessing(
                        EventLoggingStatus.FAILURE,
                        event,
                        Pair("errorInfo", "$coreFailure")
                    )
            }
    }

    private suspend fun updateProtocol(event: Event.Conversation.ConversationProtocol) = wrapStorageRequest {
        conversationDAO.updateConversationProtocol(
            event.conversationId.toDao(),
            event.protocol.toDao()
        )
    }

}
