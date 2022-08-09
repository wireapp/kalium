package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepositoryTest
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.QualifiedID
import com.wire.kalium.network.api.conversation.ConvProtocol
import com.wire.kalium.network.api.conversation.ConversationMemberAddedDTO
import com.wire.kalium.network.api.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.conversation.ConversationMemberRemovedDTO
import com.wire.kalium.network.api.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
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
        ProtocolInfo.Proteus,
        MutedConversationStatus.AllAllowed,
        null,
        null,
        null,
        lastReadDate = "2022-03-30T15:36:00.000Z",
        access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
        accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST)
    )
    val SELF = Conversation(
        ID.copy(value = "SELF ID"),
        "SELF Name",
        Conversation.Type.SELF,
        TestTeam.TEAM_ID,
        ProtocolInfo.Proteus,
        MutedConversationStatus.AllAllowed,
        null,
        null,
        null,
        lastReadDate = "2022-03-30T15:36:00.000Z",
        access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
        accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST)
    )

    fun GROUP(protocolInfo: ProtocolInfo = ProtocolInfo.Proteus) = Conversation(
        ID.copy(value = if (protocolInfo is ProtocolInfo.MLS) protocolInfo.groupId else "GROUP ID"),
        "GROUP Name",
        Conversation.Type.GROUP,
        TestTeam.TEAM_ID,
        protocolInfo,
        MutedConversationStatus.AllAllowed,
        null,
        null,
        null,
        lastReadDate = "2022-03-30T15:36:00.000Z",
        access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
        accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST)
    )

    fun GROUP_ENTITY(protocolInfo: ConversationEntity.ProtocolInfo = ConversationEntity.ProtocolInfo.Proteus) = ConversationEntity(
        ENTITY_ID.copy(value = if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.groupId else "GROUP ID"),
        "convo name",
        ConversationEntity.Type.GROUP,
        "teamId",
        protocolInfo,
        lastNotificationDate = null,
        lastModifiedDate = "2022-03-30T15:36:00.000Z",
        lastReadDate = "2022-03-30T15:36:00.000Z",
        access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
        accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER)
    )

    fun one_on_one(convId: ConversationId) = Conversation(
        convId,
        "ONE_ON_ONE Name",
        Conversation.Type.ONE_ON_ONE,
        TestTeam.TEAM_ID,
        ProtocolInfo.Proteus,
        MutedConversationStatus.AllAllowed,
        null,
        null,
        null,
        lastReadDate = "2022-03-30T15:36:00.000Z",
        access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
        accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST)
    )

    val NETWORK_ID = QualifiedID("valueConversation", "domainConversation")
    val USER_1 = UserId("member1", "domainMember")
    val MEMBER_TEST1 = Member(USER_1, Member.Role.Admin)
    val USER_2 = UserId("member2", "domainMember")
    val MEMBER_TEST2 = Member(USER_2, Member.Role.Member)
    val NETWORK_USER_ID1 = com.wire.kalium.network.api.UserId(value = "member1", domain = "domainMember")
    val NETWORK_USER_ID2 = com.wire.kalium.network.api.UserId(value = "member2", domain = "domainMember")
    val USER_ID1 = UserId(value = "member1", domain = "domainMember")

    val CONVERSATION_RESPONSE = ConversationResponse(
        "creator",
        ConversationMembersResponse(
            ConversationMemberDTO.Self(MapperProvider.idMapper().toApiModel(TestUser.SELF.id), "wire_admin"),
            emptyList()
        ),
        ConversationRepositoryTest.GROUP_NAME,
        NETWORK_ID,
        null,
        0UL,
        ConversationResponse.Type.GROUP,
        0,
        null,
        ConvProtocol.PROTEUS,
        lastEventTime = "2022-03-30T15:36:00.000Z",
        access = setOf(ConversationAccessDTO.INVITE, ConversationAccessDTO.CODE),
        accessRole = setOf(
            ConversationAccessRoleDTO.GUEST,
            ConversationAccessRoleDTO.TEAM_MEMBER,
            ConversationAccessRoleDTO.NON_TEAM_MEMBER
        )
    )

    val ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE =
        ConversationMemberAddedDTO.Changed(
            "conversation.member-join",
            qualifiedConversationId = NETWORK_ID,
            fromUser = NETWORK_USER_ID1,
            time = "2022-03-30T15:36:00.000Z"
        )

    val REMOVE_MEMBER_FROM_CONVERSATION_SUCCESSFUL_RESPONSE =
        ConversationMemberRemovedDTO.Changed(
            "conversation.member-leave",
            qualifiedConversationId = NETWORK_ID,
            fromUser = NETWORK_USER_ID1,
            time = "2022-03-30T15:36:00.000Z"
        )

    val ENTITY_ID = QualifiedIDEntity("valueConversation", "domainConversation")
    val ENTITY = ConversationEntity(
        ENTITY_ID,
        "convo name",
        ConversationEntity.Type.SELF,
        "teamId",
        ConversationEntity.ProtocolInfo.Proteus,
        lastNotificationDate = null,
        lastModifiedDate = "2022-03-30T15:36:00.000Z",
        lastReadDate = "2022-03-30T15:36:00.000Z",
        access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
        accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER)
    )

    val CONVERSATION = Conversation(
        ConversationId("conv_id", "domain"),
        "ONE_ON_ONE Name",
        Conversation.Type.ONE_ON_ONE,
        TestTeam.TEAM_ID,
        ProtocolInfo.Proteus,
        MutedConversationStatus.AllAllowed,
        null,
        null,
        null,
        access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
        accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST),
        lastReadDate = "2022-03-30T15:36:00.000Z"
    )

    val MLS_CONVERSATION = Conversation(
        ConversationId("conv_id", "domain"),
        "MLS Name",
        Conversation.Type.ONE_ON_ONE,
        TestTeam.TEAM_ID,
        ProtocolInfo.MLS("group_id", ProtocolInfo.MLS.GroupState.PENDING_JOIN, 0UL),
        MutedConversationStatus.AllAllowed,
        null,
        null,
        null,
        access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
        accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST),
        lastReadDate = "2022-03-30T15:36:00.000Z"
    )
}
