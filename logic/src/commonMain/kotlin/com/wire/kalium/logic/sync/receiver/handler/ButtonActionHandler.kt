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
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MessageButtonId
import com.wire.kalium.logic.data.id.MessageId
import com.wire.kalium.logic.data.message.CompositeMessageRepository
import com.wire.kalium.logic.data.user.UserId
import io.mockative.Mockable

@Mockable
internal interface ButtonActionHandler {
    suspend fun handle(
        conversationId: ConversationId,
        senderId: UserId,
        messageId: MessageId,
        buttonId: MessageButtonId,
    )
}

internal class ButtonActionHandlerImpl internal constructor(
    private val selfUserId: UserId,
    private val compositeMessageRepository: CompositeMessageRepository,
    logger: KaliumLogger = kaliumLogger,
) : ButtonActionHandler {

    private val logger = logger.withTextTag("ButtonActionHandler")

    override suspend fun handle(
        conversationId: ConversationId,
        senderId: UserId,
        messageId: MessageId,
        buttonId: MessageButtonId
    ) {
        if (senderId != selfUserId) {
            logger.d("Ignoring button action from ${senderId.toLogString()}, as it is not from self user.")
            return
        }
        compositeMessageRepository.markSelected(
            messageId = messageId,
            conversationId = conversationId,
            buttonId = buttonId
        )
    }

}
