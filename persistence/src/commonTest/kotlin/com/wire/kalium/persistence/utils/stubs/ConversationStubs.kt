package com.wire.kalium.persistence.utils.stubs

import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity

fun newConversationEntity(id: String = "test") = ConversationEntity(
    id = QualifiedIDEntity(id, "wire.com"),
    name = "conversation1",
    type = ConversationEntity.Type.ONE_ON_ONE,
    teamId = "teamID",
    protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
    lastNotificationDate = null,
    lastModifiedDate = "2022-03-30T15:36:00.000Z",
    access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
    accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER)
)

fun newConversationEntity(
    id: QualifiedIDEntity,
    lastReadDate: String = "",
    lastModified: String = "2022-03-30T15:36:00.000Z"
) = ConversationEntity(
    id = id,
    name = "conversation1",
    type = ConversationEntity.Type.ONE_ON_ONE,
    teamId = "teamID",
    protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
    lastNotificationDate = null,
    lastReadDate = lastReadDate,
    lastModifiedDate = lastModified,
    access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
    accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER)
)
