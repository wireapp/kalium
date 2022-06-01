package com.wire.kalium.persistence.utils.stubs

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity

@Suppress("LongParameterList")
fun newMessageEntity(
    id: String = "testMessage",
    content: MessageEntity.MessageEntityContent = MessageEntity.MessageEntityContent.TextMessageContent("Test Text"),
    conversationId: QualifiedIDEntity = QualifiedIDEntity("convId", "convDomain"),
    senderUserId: QualifiedIDEntity = QualifiedIDEntity("senderId", "senderDomain"),
    senderClientId: String = "senderClientId",
    status: MessageEntity.Status = MessageEntity.Status.PENDING,
    editStatus : MessageEntity.EditStatus = MessageEntity.EditStatus.NotEdited
) = MessageEntity(
    id = id,
    content = content,
    conversationId = conversationId,
    date = "date time",
    senderUserId = senderUserId,
    senderClientId = senderClientId,
    status = status,
    editStatus = editStatus,
    visibility = MessageEntity.Visibility.VISIBLE
)
