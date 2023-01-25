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
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.conversation.ReceiptModeMapper
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.ConversationDAO

interface ReceiptModeUpdateEventHandler {
    suspend fun handle(event: Event.Conversation.ConversationReceiptMode)
}

internal class ReceiptModeUpdateEventHandlerImpl(
    private val conversationDAO: ConversationDAO,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val receiptModeMapper: ReceiptModeMapper = MapperProvider.receiptModeMapper()
) : ReceiptModeUpdateEventHandler {

    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handle(event: Event.Conversation.ConversationReceiptMode) {
        updateReceiptMode(event)
            .onSuccess {
                // todo: handle system message, we need to add it to db, see: conversation renamed
                logger.d("The Conversation receipt mode was updated: ${event.conversationId.toString().obfuscateId()}")
            }
            .onFailure { coreFailure ->
                logger.e("Error updating receipt mode for conversation [${event.conversationId.toString().obfuscateId()}] $coreFailure")
            }
    }

    private suspend fun updateReceiptMode(event: Event.Conversation.ConversationReceiptMode) = wrapStorageRequest {
        conversationDAO.updateConversationReceiptMode(
            event.conversationId.toDao(),
            receiptModeMapper.toDaoModel(event.receiptMode)
        )
    }

}
