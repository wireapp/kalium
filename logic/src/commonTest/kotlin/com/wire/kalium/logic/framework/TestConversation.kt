package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.network.api.QualifiedID
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity

object TestConversation {
    val ID = ConversationId("valueConvo", "domainConvo")

    val ONE_ON_ONE = Conversation(ID.copy(value = "1O1 ID"), "ONE_ON_ONE Name", Conversation.Type.ONE_ON_ONE, TestTeam.TEAM_ID, MutedConversationStatus.AllAllowed)
    val SELF = Conversation(ID.copy(value = "SELF ID"), "SELF Name", Conversation.Type.SELF, TestTeam.TEAM_ID, MutedConversationStatus.AllAllowed)
    val GROUP = Conversation(ID.copy(value = "GROUP ID"), "GROUP Name", Conversation.Type.GROUP, TestTeam.TEAM_ID, MutedConversationStatus.AllAllowed)

    val NETWORK_ID = QualifiedID("valueConversation", "domainConversation")

    val ENTITY_ID = QualifiedIDEntity("valueConversation", "domainConversation")
    val ENTITY = ConversationEntity(ENTITY_ID, "convo name", ConversationEntity.Type.SELF, "teamId", ConversationEntity.ProtocolInfo.Proteus)
}
