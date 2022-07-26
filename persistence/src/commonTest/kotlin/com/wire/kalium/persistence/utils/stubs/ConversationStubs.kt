package com.wire.kalium.persistence.utils.stubs

import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity

fun newConversationEntity(id: String = "test") = ConversationEntity(
    QualifiedIDEntity(id, "wire.com"),
    "conversation1",
    ConversationEntity.Type.ONE_ON_ONE,
    "teamID",
    ConversationEntity.ProtocolInfo.Proteus,
    lastNotificationDate = null,
    lastModifiedDate = "2022-03-30T15:36:00.000Z",
    access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
    accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER)
)

fun newConversationEntity(id: QualifiedIDEntity) = ConversationEntity(
    id,
    "conversation1",
    ConversationEntity.Type.ONE_ON_ONE,
    "teamID",
    ConversationEntity.ProtocolInfo.Proteus,
    lastNotificationDate = null,
    lastModifiedDate = "2022-03-30T15:36:00.000Z",
    access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
    accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER)
)
