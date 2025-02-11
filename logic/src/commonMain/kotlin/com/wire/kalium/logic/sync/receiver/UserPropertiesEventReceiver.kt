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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.createEventProcessingLogger

internal interface UserPropertiesEventReceiver : EventReceiver<Event.UserProperty>

internal class UserPropertiesEventReceiverImpl internal constructor(
    private val userConfigRepository: UserConfigRepository,
    private val conversationFolderRepository: ConversationFolderRepository
) : UserPropertiesEventReceiver {

    override suspend fun onEvent(event: Event.UserProperty, deliveryInfo: EventDeliveryInfo): Either<CoreFailure, Unit> {
        return when (event) {
            is Event.UserProperty.ReadReceiptModeSet -> {
                handleReadReceiptMode(event)
            }

            is Event.UserProperty.TypingIndicatorModeSet -> {
                handleTypingIndicatorMode(event)
            }

            is Event.UserProperty.FoldersUpdate -> {
                handleFoldersUpdate(event)
            }
        }
    }

    private fun handleReadReceiptMode(
        event: Event.UserProperty.ReadReceiptModeSet
    ): Either<CoreFailure, Unit> {
        val logger = kaliumLogger.createEventProcessingLogger(event)
        return userConfigRepository
            .setReadReceiptsStatus(event.value)
            .onSuccess { logger.logSuccess() }
            .onFailure { logger.logFailure(it) }
    }

    private fun handleTypingIndicatorMode(
        event: Event.UserProperty.TypingIndicatorModeSet
    ): Either<CoreFailure, Unit> {
        val logger = kaliumLogger.createEventProcessingLogger(event)
        return userConfigRepository
            .setTypingIndicatorStatus(event.value)
            .onSuccess { logger.logSuccess() }
            .onFailure { logger.logFailure(it) }
    }

    private suspend fun handleFoldersUpdate(
        event: Event.UserProperty.FoldersUpdate
    ): Either<CoreFailure, Unit> {
        val logger = kaliumLogger.createEventProcessingLogger(event)
        return conversationFolderRepository
            .updateConversationFolders(event.folders)
            .onSuccess { logger.logSuccess() }
            .onFailure { logger.logFailure(it) }
    }
}
