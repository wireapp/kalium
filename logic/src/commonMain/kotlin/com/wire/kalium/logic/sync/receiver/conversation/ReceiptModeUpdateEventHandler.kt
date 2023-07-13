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

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ReceiptModeMapper
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventLoggingStatus
import com.wire.kalium.logic.data.event.logEventProcessing
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.util.DateTimeUtil

interface ReceiptModeUpdateEventHandler {
    suspend fun handle(event: Event.Conversation.ConversationReceiptMode)
}

internal class ReceiptModeUpdateEventHandlerImpl(
    private val conversationDAO: ConversationDAO,
    private val persistMessage: PersistMessageUseCase,
    private val receiptModeMapper: ReceiptModeMapper = MapperProvider.receiptModeMapper()
) : ReceiptModeUpdateEventHandler {

    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handle(event: Event.Conversation.ConversationReceiptMode) {
        updateReceiptMode(event)
            .onSuccess {
                val message = Message.System(
                    uuid4().toString(),
                    MessageContent.ConversationReceiptModeChanged(
                        receiptMode = event.receiptMode == Conversation.ReceiptMode.ENABLED
                    ),
                    event.conversationId,
                    DateTimeUtil.currentIsoDateTimeString(),
                    event.senderUserId,
                    Message.Status.SENT,
                    Message.Visibility.VISIBLE,
                    expirationData = null
                )

                persistMessage(message)
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.SUCCESS,
                        event
                    )
            }
            .onFailure { coreFailure ->
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.FAILURE,
                        event,
                        Pair("errorInfo", "$coreFailure")
                    )
            }
    }

    private suspend fun updateReceiptMode(event: Event.Conversation.ConversationReceiptMode) = wrapStorageRequest {
        conversationDAO.updateConversationReceiptMode(
            event.conversationId.toDao(),
            receiptModeMapper.toDaoModel(event.receiptMode)
        )
    }

}
