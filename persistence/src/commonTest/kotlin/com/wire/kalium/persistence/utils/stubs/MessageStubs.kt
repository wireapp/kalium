package com.wire.kalium.persistence.utils.stubs

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity

fun newMessageEntity(
    id: String = "testMessage",
    content: MessageEntity.MessageEntityContent = MessageEntity.MessageEntityContent.TextMessageContent("Test Text"),
    conversationId: QualifiedIDEntity = QualifiedIDEntity("convId", "convDomain"),
    senderUserId: QualifiedIDEntity = QualifiedIDEntity("senderId", "senderDomain"),
    senderClientId: String = "senderClientId",
    status: MessageEntity.Status = MessageEntity.Status.PENDING
) = MessageEntity(
    id, content, conversationId, date = "date time", senderUserId, senderClientId, status, visibility = MessageEntity.Visibility.VISIBLE
)
