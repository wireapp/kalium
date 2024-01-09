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
package com.wire.kalium.logic.data.message

import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.serialization.toJsonElement

data class BroadcastMessage(
    val id: String,
    val content: MessageContent.Signaling,
    val date: String,
    val senderUserId: UserId,
    val status: Message.Status,
    val isSelfMessage: Boolean,
    val senderClientId: ClientId,
) {

    @Suppress("LongMethod")
    fun toLogString(): String {
        val typeKey = "type"

        val properties: MutableMap<String, Any> = when (content) {
            is MessageContent.TextEdited -> mutableMapOf(
                typeKey to "textEdit"
            )

            is MessageContent.Calling -> mutableMapOf(
                typeKey to "calling"
            )

            is MessageContent.ClientAction -> mutableMapOf(
                typeKey to "clientAction"
            )

            is MessageContent.DeleteMessage -> mutableMapOf(
                typeKey to "delete"
            )

            is MessageContent.DeleteForMe -> mutableMapOf(
                typeKey to "deleteForMe",
                "messageId" to content.messageId.obfuscateId(),
            )

            is MessageContent.LastRead -> mutableMapOf(
                typeKey to "lastRead",
                "time" to "${content.time}",
            )

            is MessageContent.Availability -> mutableMapOf(
                typeKey to "availability",
            )

            is MessageContent.Cleared -> mutableMapOf(
                typeKey to "cleared",
            )

            is MessageContent.Reaction -> mutableMapOf(
                typeKey to "reaction",
            )

            is MessageContent.Receipt -> mutableMapOf(
                typeKey to "receipt",
                "content" to content.toLogMap(),
            )

            MessageContent.Ignored -> mutableMapOf(
                typeKey to "ignored"
            )

            is MessageContent.ButtonAction -> mutableMapOf(
                typeKey to "buttonAction",
            )
            is MessageContent.ButtonActionConfirmation -> mutableMapOf(
                typeKey to "buttonActionConfirmation",
            )
        }

        val standardProperties = mapOf(
            "id" to id.obfuscateId(),
            "date" to date,
            "senderUserId" to senderUserId.value.obfuscateId(),
            "status" to "$status",
            "senderClientId" to senderClientId.value.obfuscateId(),
        )

        properties.putAll(standardProperties)

        return "${properties.toMap().toJsonElement()}"
    }
}
