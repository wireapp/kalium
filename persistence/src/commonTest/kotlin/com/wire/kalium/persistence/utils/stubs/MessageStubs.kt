package com.wire.kalium.persistence.utils.stubs

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
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
    creationInstant: Instant = Instant.parse("2022-03-30T15:36:00.000Z"),
    visibility: MessageEntity.Visibility = MessageEntity.Visibility.VISIBLE,
    senderName: String = "senderName",
    expectsReadConfirmation: Boolean = false,
) = MessageEntity.Regular(
    id = id,
    content = content,
    conversationId = conversationId,
    creationInstant = creationInstant,
    senderUserId = senderUserId,
    senderClientId = senderClientId,
    status = status,
    editStatus = editStatus,
    visibility = visibility,
    senderName = senderName,
    expectsReadConfirmation = expectsReadConfirmation
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
    creationInstant: Instant = Instant.parse("2022-03-30T15:36:00.000Z"),
    visibility: MessageEntity.Visibility = MessageEntity.Visibility.VISIBLE
) = MessageEntity.System(
    id = id,
    content = content,
    conversationId = conversationId,
    creationInstant = creationInstant,
    senderUserId = senderUserId,
    status = status,
    visibility = visibility,
    senderName = "senderName"
)
