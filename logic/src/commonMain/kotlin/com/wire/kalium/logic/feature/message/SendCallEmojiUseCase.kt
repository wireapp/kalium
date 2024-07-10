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

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.network.tools.KtxSerializer
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString

class SendCallEmojiUseCase(
    private val selfClientIdProvider: CurrentClientIdProvider,
    private val selfUserId: UserId,
    private val messageSender: MessageSender
) {
    suspend operator fun invoke(
        conversationId: ConversationId,
        emoji: List<String>
    ) {
        val callingValue = MessageContent.Calling.CallingValue(
            emojis = emoji,
            type = "EMOJIS",
            targets = null,
        )

        val content = MessageContent.Calling(
            value = KtxSerializer.json.encodeToString(callingValue),
            conversationId = conversationId
        )
        val message = Message.Signaling(
            content = content,
            conversationId = conversationId,
            date = Clock.System.now(),
            expirationData = null,
            isSelfMessage = false,
            id = uuid4().toString(),
            senderUserId = selfUserId,
            senderClientId = selfClientIdProvider().getOrElse { throw IllegalStateException("No client id") },
            status = Message.Status.Pending,
            )

        messageSender.sendMessage(message)
    }
}
