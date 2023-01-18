package com.wire.kalium.persistence.utils.stubs

import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant

fun newConversationEntity(id: String = "test") = ConversationEntity(
    id = QualifiedIDEntity(id, "wire.com"),
    name = "conversation1",
    type = ConversationEntity.Type.ONE_ON_ONE,
    teamId = "teamID",
    protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
    creatorId = "someValue",
    lastNotificationDate = null,
    lastModifiedDate = "2022-03-30T15:36:00.000Z".toInstant(),
    lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
    access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
    accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
    receiptMode = ConversationEntity.ReceiptMode.DISABLED
)

fun newConversationEntity(
    id: QualifiedIDEntity,
    lastReadDate: Instant = Instant.parse("1970-01-01T00:00:00.000Z"),
    lastModified: Instant = Instant.parse("2022-03-30T15:36:00.000Z")
) = ConversationEntity(
    id = id,
    name = "conversation1",
    type = ConversationEntity.Type.ONE_ON_ONE,
    teamId = "teamID",
    protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
    creatorId = "someValue",
    lastNotificationDate = null,
    lastReadDate = lastReadDate,
    lastModifiedDate = lastModified,
    access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
    accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
    receiptMode = ConversationEntity.ReceiptMode.DISABLED
)
