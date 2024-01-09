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

package samples.logic

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.SendEditTextMessageUseCase
import com.wire.kalium.logic.feature.message.SendTextMessageUseCase

object MessageUseCases {

    suspend fun sendingBasicTextMessage(
        sendTextMessageUseCase: SendTextMessageUseCase,
        conversationId: ConversationId
    ) {
        // Sending a simple text message
        sendTextMessageUseCase.invoke(
            conversationId = conversationId,
            text = "Hello"
        )
    }

    suspend fun sendingTextMessageWithMentions(
        sendTextMessageUseCase: SendTextMessageUseCase,
        conversationId: ConversationId,
        johnUserId: UserId,
        selfUserId: UserId
    ) {
        // Sending a text message with mention
        val text = "Hello, @John"
        val johnMention = MessageMention(
            start = 8, // The index of the @ in the text above
            length = 5, // The length of the mention (including the @)
            userId = johnUserId, // ID of the user being mentioned
            isSelfMention = selfUserId == johnUserId // Whether the mention is for the current user
        )
        sendTextMessageUseCase.invoke(
            conversationId = conversationId,
            text = text,
            mentions = listOf(johnMention)
        )
    }

    suspend fun sendingEditBasicTextMessage(
        editTextMessageUseCase: SendEditTextMessageUseCase,
        conversationId: ConversationId,
        originalMessageId: String,
        johnUserId: UserId,
        selfUserId: UserId
    ) {
        // Editing a simple text message
        val johnMention = MessageMention(
            start = 8, // The index of the @ in the text above
            length = 5, // The length of the mention (including the @)
            userId = johnUserId, // ID of the user being mentioned
            isSelfMention = selfUserId == johnUserId // Whether the mention is for the current user
        )
        editTextMessageUseCase.invoke(
            conversationId = conversationId,
            text = "Hello",
            originalMessageId = originalMessageId,
            mentions = listOf(johnMention)
        )
    }
}
