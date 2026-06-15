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

import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import kotlinx.datetime.Clock
import kotlin.uuid.Uuid

internal object EditMessageBuilder {
    fun buildTextEditSignaling(
        originalMessage: Message.Regular,
        originalContent: MessageContent.Text,
        editedMessageId: String = Uuid.random().toString(),
        date: kotlinx.datetime.Instant = Clock.System.now(),
    ): Message.Signaling = Message.Signaling(
        id = editedMessageId,
        content = MessageContent.TextEdited(
            editMessageId = originalMessage.id,
            newContent = originalContent.value,
            newMentions = originalContent.mentions,
        ),
        conversationId = originalMessage.conversationId,
        date = date,
        senderUserId = originalMessage.senderUserId,
        senderClientId = originalMessage.senderClientId,
        status = Message.Status.Pending,
        isSelfMessage = true,
        expirationData = null,
    )
}
