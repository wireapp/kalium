package com.wire.kalium.persistence.utils.stubs

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent

fun newMessageEntity(
    id: String = "testMessage",
    content: MessageEntityContent.Client = MessageEntityContent.Text("Test Text"),
    conversationId: QualifiedIDEntity = QualifiedIDEntity("convId", "convDomain"),
    senderUserId: QualifiedIDEntity = QualifiedIDEntity("senderId", "senderDomain"),
    senderClientId: String = "senderClientId",
    status: MessageEntity.Status = MessageEntity.Status.PENDING,
    date: String = "2022-03-30T15:36:00.000Z"
) = MessageEntity.Client(
    id = id,
    content = content,
    conversationId = conversationId,
    date = date,
    senderUserId = senderUserId,
    senderClientId = senderClientId,
    status = status,
    visibility = MessageEntity.Visibility.VISIBLE
)
