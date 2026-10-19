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
