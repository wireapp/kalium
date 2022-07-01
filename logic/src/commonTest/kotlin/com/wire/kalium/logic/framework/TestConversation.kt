package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepositoryTest
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.QualifiedID
import com.wire.kalium.network.api.conversation.ConvProtocol
import com.wire.kalium.network.api.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity

object TestConversation {
    val ID = ConversationId("valueConvo", "domainConvo")

    fun id(suffix: Int = 0) = ConversationId("valueConvo_$suffix", "domainConvo")

    val ONE_ON_ONE = Conversation(
        ID.copy(value = "1O1 ID"),
        "ONE_ON_ONE Name",
        Conversation.Type.ONE_ON_ONE,
        TestTeam.TEAM_ID,
        MutedConversationStatus.AllAllowed,
        null,
        null
    )
    val SELF = Conversation(
        ID.copy(value = "SELF ID"),
        "SELF Name",
        Conversation.Type.SELF,
        TestTeam.TEAM_ID,
        MutedConversationStatus.AllAllowed,
        null,
        null
    )
    val GROUP = Conversation(
        ID.copy(value = "GROUP ID"),
        "GROUP Name",
        Conversation.Type.GROUP,
        TestTeam.TEAM_ID,
        MutedConversationStatus.AllAllowed,
        null,
        null
    )

    fun one_on_one(convId: ConversationId) = Conversation(
        convId,
        "ONE_ON_ONE Name",
        Conversation.Type.ONE_ON_ONE,
        TestTeam.TEAM_ID,
        MutedConversationStatus.AllAllowed,
        null,
        null
    )

    val NETWORK_ID = QualifiedID("valueConversation", "domainConversation")

    val CONVERSATION_RESPONSE = ConversationResponse(
        "creator",
        ConversationMembersResponse(
            ConversationMemberDTO.Self(MapperProvider.idMapper().toApiModel(TestUser.SELF.id), "wire_admin"),
            emptyList()
        ),
        ConversationRepositoryTest.GROUP_NAME,
        NETWORK_ID,
        null,
        ConversationResponse.Type.GROUP,
        0,
        null,
        ConvProtocol.PROTEUS,
        lastEventTime = "2022-03-30T15:36:00.000Z"
    )

    val ENTITY_ID = QualifiedIDEntity("valueConversation", "domainConversation")
    val ENTITY = ConversationEntity(
        ENTITY_ID,
        "convo name",
        ConversationEntity.Type.SELF,
        "teamId",
        ConversationEntity.ProtocolInfo.Proteus,
        lastNotificationDate = null,
        lastModifiedDate = "2022-03-30T15:36:00.000Z"
    )
}
