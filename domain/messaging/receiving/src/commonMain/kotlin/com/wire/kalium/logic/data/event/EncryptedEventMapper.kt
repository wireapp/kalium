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

package com.wire.kalium.logic.data.event

import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import kotlin.io.encoding.Base64

fun newMessage(
    id: String,
    eventContentDTO: EventContentDTO.Conversation.NewMessageDTO,
) = Event.Conversation.NewMessage(
    id,
    eventContentDTO.qualifiedConversation.toModel(),
    eventContentDTO.qualifiedFrom.toModel(),
    ClientId(eventContentDTO.data.sender),
    eventContentDTO.time,
    eventContentDTO.data.text,
    eventContentDTO.data.encryptedExternalData?.let {
        EncryptedData(Base64.decode(it))
    }
)

fun newMLSMessage(
    id: String,
    eventContentDTO: EventContentDTO.Conversation.NewMLSMessageDTO,
) = Event.Conversation.NewMLSMessage(
    id = id,
    conversationId = eventContentDTO.qualifiedConversation.toModel(),
    subconversationId = eventContentDTO.subconversation?.let { SubconversationId(it) },
    senderUserId = eventContentDTO.qualifiedFrom.toModel(),
    messageInstant = eventContentDTO.time,
    content = eventContentDTO.message
)

/** Targeted messages decrypt like other MLS messages; they are never sent in subconversations. */
fun newMLSMessage(
    id: String,
    eventContentDTO: EventContentDTO.Conversation.NewMLSTargetedMessageDTO,
) = Event.Conversation.NewMLSMessage(
    id = id,
    conversationId = eventContentDTO.qualifiedConversation.toModel(),
    subconversationId = null,
    senderUserId = eventContentDTO.qualifiedFrom.toModel(),
    messageInstant = eventContentDTO.time,
    content = eventContentDTO.message,
    targeted = true
)

/** Transient messages decrypt like other MLS messages; they are never sent in subconversations. */
fun newMLSMessage(
    id: String,
    eventContentDTO: EventContentDTO.Conversation.NewMLSTransientMessageDTO,
) = Event.Conversation.NewMLSMessage(
    id = id,
    conversationId = eventContentDTO.qualifiedConversation.toModel(),
    subconversationId = null,
    senderUserId = eventContentDTO.qualifiedFrom.toModel(),
    messageInstant = eventContentDTO.time,
    content = eventContentDTO.message,
    transient = true
)

/** Transient targeted messages decrypt like other MLS messages; they are never sent in subconversations. */
fun newMLSMessage(
    id: String,
    eventContentDTO: EventContentDTO.Conversation.NewMLSTransientTargetedMessageDTO,
) = Event.Conversation.NewMLSMessage(
    id = id,
    conversationId = eventContentDTO.qualifiedConversation.toModel(),
    subconversationId = null,
    senderUserId = eventContentDTO.qualifiedFrom.toModel(),
    messageInstant = eventContentDTO.time,
    content = eventContentDTO.message,
    targeted = true,
    transient = true
)

/** The MLS message carried by this event, or null if the event is not an MLS message. */
fun EventContentDTO.newMLSMessageOrNull(id: String): Event.Conversation.NewMLSMessage? = when (this) {
    is EventContentDTO.Conversation.NewMLSMessageDTO -> newMLSMessage(id, this)
    is EventContentDTO.Conversation.NewMLSTargetedMessageDTO -> newMLSMessage(id, this)
    is EventContentDTO.Conversation.NewMLSTransientMessageDTO -> newMLSMessage(id, this)
    is EventContentDTO.Conversation.NewMLSTransientTargetedMessageDTO -> newMLSMessage(id, this)
    else -> null
}

fun newConversation(
    id: String,
    content: EventContentDTO.Conversation.NewConversationDTO,
    payloadIndex: Int = 0,
) = Event.Conversation.NewConversation(
    metadataSystemMessageId(id, payloadIndex),
    content.qualifiedConversation.toModel(),
    content.qualifiedFrom.toModel(),
    content.time,
    content.data
)

fun newMeetingConversation(
    id: String,
    content: EventContentDTO.Conversation.NewMeetingConversationDTO,
    payloadIndex: Int = 0,
) = Event.Conversation.NewConversation(
    metadataSystemMessageId(id, payloadIndex),
    content.qualifiedConversation.toModel(),
    content.qualifiedFrom.toModel(),
    content.time,
    content.data
)
