package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.Member
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.conversation.ConversationRepositoryTest
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberAddedDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberRemovedDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.ConversationViewEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.datetime.Instant

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
        accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST),
        isSelfUserMember = true,
        isCreator = false
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
        accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST),
        isSelfUserMember = true,
        isCreator = false
    )

    fun GROUP(protocolInfo: ProtocolInfo = ProtocolInfo.Proteus) = Conversation(
        ID,
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
        accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST),
        isSelfUserMember = true,
        isCreator = true
    )

    fun GROUP_ENTITY(protocolInfo: ConversationEntity.ProtocolInfo = ConversationEntity.ProtocolInfo.Proteus) = ConversationEntity(
        ENTITY_ID.copy(value = if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.groupId else "GROUP ID"),
        "convo name",
        ConversationEntity.Type.GROUP,
        "teamId",
        protocolInfo,
        creatorId = "someValue",
        lastNotificationDate = null,
        lastModifiedDate = "2022-03-30T15:36:00.000Z",
        lastReadDate = "2022-03-30T15:36:00.000Z",
        access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
        accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER)
    )

    fun GROUP_VIEW_ENTITY(protocolInfo: ConversationEntity.ProtocolInfo = ConversationEntity.ProtocolInfo.Proteus) = ConversationViewEntity(
        ENTITY_ID.copy(value = if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.groupId else "GROUP ID"),
        "convo name",
        ConversationEntity.Type.GROUP,
        null,
        null,
        ConversationEntity.MutedStatus.ALL_ALLOWED,
        "teamId",
        lastModifiedDate = "2022-03-30T15:36:00.000Z",
        lastReadDate = "2022-03-30T15:36:00.000Z",
        null,
        null,
        null,
        false,
        null,
        null,
        isCreator = 0L,
        lastNotificationDate = null,
        0,
        isMember = 1L,
        protocolInfo = protocolInfo,
        creatorId = "someValue",
        accessList = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
        accessRoleList = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
        protocol = ConversationEntity.Protocol.MLS,
        mlsCipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256,
        mlsEpoch = 0L,
        mlsGroupId = null,
        mlsLastKeyingMaterialUpdate = 0L,
        mlsGroupState = ConversationEntity.GroupState.ESTABLISHED,
        mlsProposalTimer = null,
        mutedTime = 0L,
        removedBy = null,
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
        accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST),
        isSelfUserMember = true,
        isCreator = false
    )

    val NETWORK_ID = QualifiedID("valueConversation", "domainConversation")
    val USER_1 = UserId("member1", "domainMember")
    val MEMBER_TEST1 = Member(USER_1, Member.Role.Admin)
    val USER_2 = UserId("member2", "domainMember")
    val MEMBER_TEST2 = Member(USER_2, Member.Role.Member)
    val NETWORK_USER_ID1 =
        com.wire.kalium.network.api.base.model.UserId(value = "member1", domain = "domainMember")
    val NETWORK_USER_ID2 =
        com.wire.kalium.network.api.base.model.UserId(value = "member2", domain = "domainMember")
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
        ),
        mlsCipherSuiteTag = null
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

    val GROUP_ID = GroupID("mlsGroupId")
    val ENTITY_ID = QualifiedIDEntity("valueConversation", "domainConversation")
    val ENTITY = ConversationEntity(
        ENTITY_ID,
        "convo name",
        ConversationEntity.Type.SELF,
        "teamId",
        ConversationEntity.ProtocolInfo.Proteus,
        creatorId = "someValue",
        lastNotificationDate = null,
        lastModifiedDate = "2022-03-30T15:36:00.000Z",
        lastReadDate = "2022-03-30T15:36:00.000Z",
        access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
        accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER)
    )
    val VIEW_ENTITY = ConversationViewEntity(
        ENTITY_ID,
        "convo name",
        ConversationEntity.Type.SELF,
        null,
        null,
        ConversationEntity.MutedStatus.ALL_ALLOWED,
        "teamId",
        lastModifiedDate = "2022-03-30T15:36:00.000Z",
        lastReadDate = "2022-03-30T15:36:00.000Z",
        null,
        null,
        null,
        false,
        null,
        null,
        isCreator = 0L,
        lastNotificationDate = null,
        0,
        isMember = 1L,
        ConversationEntity.ProtocolInfo.Proteus,
        creatorId = "someValue",
        accessList = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
        accessRoleList = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
        protocol = ConversationEntity.Protocol.MLS,
        mlsCipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256,
        mlsEpoch = 0L,
        mlsGroupId = null,
        mlsLastKeyingMaterialUpdate = 0L,
        mlsGroupState = ConversationEntity.GroupState.ESTABLISHED,
        mlsProposalTimer = null,
        mutedTime = 0L,
        removedBy = null
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
        lastReadDate = "2022-03-30T15:36:00.000Z",
        isSelfUserMember = true,
        isCreator = false
    )

    val MLS_CONVERSATION = Conversation(
        ConversationId("conv_id", "domain"),
        "MLS Name",
        Conversation.Type.ONE_ON_ONE,
        TestTeam.TEAM_ID,
        ProtocolInfo.MLS(
            GroupID("group_id"),
            ProtocolInfo.MLS.GroupState.PENDING_JOIN,
            0UL,
            Instant.parse("2021-03-30T15:36:00.000Z"),
            cipherSuite = Conversation.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        ),
        MutedConversationStatus.AllAllowed,
        null,
        null,
        null,
        access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
        accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST),
        lastReadDate = "2022-03-30T15:36:00.000Z",
        isSelfUserMember = true,
        isCreator = false
    )
}
