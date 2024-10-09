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

package com.wire.kalium.persistence.utils.stubs

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDetailsEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.draft.MessageDraftEntity
import kotlinx.datetime.Instant

@Suppress("LongParameterList")
fun newRegularMessageEntity(
    id: String = "testMessage",
    content: MessageEntityContent.Regular = MessageEntityContent.Text("Test Text"),
    conversationId: QualifiedIDEntity = QualifiedIDEntity("convId", "convDomain"),
    senderUserId: QualifiedIDEntity = QualifiedIDEntity("senderId", "senderDomain"),
    senderClientId: String = "senderClientId",
    status: MessageEntity.Status = MessageEntity.Status.PENDING,
    editStatus: MessageEntity.EditStatus = MessageEntity.EditStatus.NotEdited,
    date: Instant = Instant.parse("2022-03-30T15:36:00.000Z"),
    visibility: MessageEntity.Visibility = MessageEntity.Visibility.VISIBLE,
    senderName: String = "senderName",
    expectsReadConfirmation: Boolean = false,
    expireAfterMs: Long? = null,
    selfDeletionEndDate: Instant? = null,
    sender: UserDetailsEntity? = null
) = MessageEntity.Regular(
    id = id,
    content = content,
    conversationId = conversationId,
    date = date,
    senderUserId = senderUserId,
    senderClientId = senderClientId,
    status = status,
    editStatus = editStatus,
    visibility = visibility,
    senderName = senderName,
    expectsReadConfirmation = expectsReadConfirmation,
    readCount = 0,
    expireAfterMs = expireAfterMs,
    selfDeletionEndDate = selfDeletionEndDate,
    sender = sender
)

@Suppress("LongParameterList")
fun newSystemMessageEntity(
    id: String = "testMessage",
    content: MessageEntityContent.System = MessageEntityContent.MemberChange(
        listOf(UserIDEntity("value", "domain")),
        MessageEntity.MemberChangeType.REMOVED
    ),
    conversationId: QualifiedIDEntity = QualifiedIDEntity("convId", "convDomain"),
    senderUserId: QualifiedIDEntity = QualifiedIDEntity("senderId", "senderDomain"),
    status: MessageEntity.Status = MessageEntity.Status.PENDING,
    date: Instant = Instant.parse("2022-03-30T15:36:00.000Z"),
    visibility: MessageEntity.Visibility = MessageEntity.Visibility.VISIBLE
) = MessageEntity.System(
    id = id,
    content = content,
    conversationId = conversationId,
    date = date,
    senderUserId = senderUserId,
    status = status,
    visibility = visibility,
    senderName = "senderName",
    expireAfterMs = null,
    selfDeletionEndDate = null,
    readCount = 0
)

fun newDraftMessageEntity(
    conversationId: QualifiedIDEntity = QualifiedIDEntity("convId", "convDomain"),
    text: String = "draft text",
    editMessageId: String? = null,
    quotedMessageId: String? = null,
    selectedMentionList: List<MessageEntity.Mention> = emptyList()
) = MessageDraftEntity(conversationId, text, editMessageId, quotedMessageId, selectedMentionList)
