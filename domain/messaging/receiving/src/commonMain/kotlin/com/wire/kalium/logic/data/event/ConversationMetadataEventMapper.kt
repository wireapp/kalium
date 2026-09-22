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

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.toModel
import com.wire.kalium.logic.data.conversation.ReceiptModeMapperImpl
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.network.api.authenticated.conversation.TypingIndicatorStatus
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO

/** Queue identity is unchanged; only later system messages need a payload suffix. */
fun metadataSystemMessageId(eventId: String, payloadIndex: Int): String =
    if (payloadIndex == 0) eventId else "$eventId:$payloadIndex"

fun conversationMessageTimerUpdate(
    id: String,
    eventContentDTO: EventContentDTO.Conversation.MessageTimerUpdate,
    payloadIndex: Int = 0,
) = Event.Conversation.ConversationMessageTimer(
    id = id,
    conversationId = eventContentDTO.qualifiedConversation.toModel(),
    messageTimer = eventContentDTO.data.messageTimer,
    senderUserId = eventContentDTO.qualifiedFrom.toModel(),
    dateTime = eventContentDTO.time,
    systemMessageId = metadataSystemMessageId(id, payloadIndex),
)

fun conversationReceiptModeUpdate(
    id: String,
    eventContentDTO: EventContentDTO.Conversation.ReceiptModeUpdate,
    payloadIndex: Int = 0,
) = Event.Conversation.ConversationReceiptMode(
    id = id,
    conversationId = eventContentDTO.qualifiedConversation.toModel(),
    receiptMode = ReceiptModeMapperImpl().fromApiToModel(eventContentDTO.data.receiptMode),
    senderUserId = eventContentDTO.qualifiedFrom.toModel(),
    systemMessageId = metadataSystemMessageId(id, payloadIndex),
)

fun conversationRenamed(
    id: String,
    event: EventContentDTO.Conversation.ConversationRenameDTO,
    payloadIndex: Int = 0,
) = Event.Conversation.RenamedConversation(
    id = id,
    conversationId = event.qualifiedConversation.toModel(),
    senderUserId = event.qualifiedFrom.toModel(),
    conversationName = event.updateNameData.conversationName,
    dateTime = event.time,
    systemMessageId = metadataSystemMessageId(id, payloadIndex),
)

fun conversationCodeDeleted(
    id: String,
    event: EventContentDTO.Conversation.CodeDeleted,
): Event.Conversation.CodeDeleted = Event.Conversation.CodeDeleted(
    id = id,
    conversationId = event.qualifiedConversation.toModel()
)

fun conversationCodeUpdated(
    id: String,
    event: EventContentDTO.Conversation.CodeUpdated
): Event.Conversation.CodeUpdated = Event.Conversation.CodeUpdated(
    id = id,
    key = event.data.key,
    code = event.data.code,
    uri = event.data.uri,
    isPasswordProtected = event.data.hasPassword,
    conversationId = event.qualifiedConversation.toModel(),
)

fun conversationChannelPermissionUpdate(
    id: String,
    eventContentDTO: EventContentDTO.Conversation.ChannelAddPermissionUpdate,
): Event.Conversation.ConversationChannelAddPermission = Event.Conversation.ConversationChannelAddPermission(
    id = id,
    conversationId = eventContentDTO.qualifiedConversation.toModel(),
    channelAddPermission = eventContentDTO.data.channelAddPermissionTypeDTO.toModel(),
    senderUserId = eventContentDTO.qualifiedFrom.toModel()
)

fun conversationAccessUpdate(
    id: String,
    event: EventContentDTO.Conversation.AccessUpdate,
) = Event.Conversation.AccessUpdate(
    id = id,
    conversationId = event.qualifiedConversation.toModel(),
    access = event.data.access.map { it.toModel() }.toSet(),
    accessRole = event.data.accessRole.map { it.toModel() }.toSet(),
    qualifiedFrom = event.qualifiedFrom.toModel(),
)

fun conversationTyping(
    id: String,
    event: EventContentDTO.Conversation.ConversationTypingDTO,
) = Event.Conversation.TypingIndicator(
    id = id,
    conversationId = event.qualifiedConversation.toModel(),
    senderUserId = event.qualifiedFrom.toModel(),
    typingIndicatorMode = when (event.status.status) {
        TypingIndicatorStatus.STARTED -> Conversation.TypingIndicatorMode.STARTED
        TypingIndicatorStatus.STOPPED -> Conversation.TypingIndicatorMode.STOPPED
    },
)
