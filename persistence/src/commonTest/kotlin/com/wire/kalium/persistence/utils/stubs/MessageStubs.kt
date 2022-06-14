package com.wire.kalium.persistence.utils.stubs

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent

@Suppress("LongParameterList")
fun newMessageEntity(
    id: String = "testMessage",
    content: MessageEntityContent.Regular = MessageEntityContent.Text("Test Text"),
    conversationId: QualifiedIDEntity = QualifiedIDEntity("convId", "convDomain"),
    senderUserId: QualifiedIDEntity = QualifiedIDEntity("senderId", "senderDomain"),
    senderClientId: String = "senderClientId",
    status: MessageEntity.Status = MessageEntity.Status.PENDING,
    editStatus : MessageEntity.EditStatus = MessageEntity.EditStatus.NotEdited,
    date: String = "2022-03-30T15:36:00.000Z",
    visibility: MessageEntity.Visibility = MessageEntity.Visibility.VISIBLE
) = MessageEntity.Regular(
    id = id,
    content = content,
    conversationId = conversationId,
    date = date,
    senderUserId = senderUserId,
    senderClientId = senderClientId,
    status = status,
    editStatus = editStatus,
    visibility = visibility
)
