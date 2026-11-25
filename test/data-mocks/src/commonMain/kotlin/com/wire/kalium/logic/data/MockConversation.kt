package com.wire.kalium.logic.data

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.Member
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberAddedResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberRemovedDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberRemovedResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationMembers
import com.wire.kalium.network.api.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponseDTO
import com.wire.kalium.network.api.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationCodeInfo
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.MemberLeaveReasonDTO
import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.api.model.ServiceAddedResponse
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationViewEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import kotlin.time.Instant

object MockConversation {
    private const val conversationValue = "valueConvo"
    private const val conversationDomain = "domainConvo"

    val ID = ConversationId(conversationValue, conversationDomain)
    val ENTITY_ID = QualifiedIDEntity(conversationValue, conversationDomain)
    val GROUP_ID = GroupID("mlsGroupId")

    fun id(suffix: Int = 0) = ConversationId("${conversationValue}_$suffix", conversationDomain)

    fun oneOnOne(
        protocolInfo: ProtocolInfo = ProtocolInfo.Proteus,
        id: ConversationId = ID.copy(value = "1O1 ID"),
        teamId: TeamId = TeamId("teamId")
    ) = Conversation(
        id = id,
        name = "ONE_ON_ONE Name",
        type = Conversation.Type.OneOnOne,
        teamId = teamId,
        protocol = protocolInfo,
        mutedStatus = MutedConversationStatus.AllAllowed,
        removedBy = null,
        lastNotificationDate = null,
        lastModifiedDate = null,
        lastReadDate = Instant.parse("2022-03-30T15:36:00.000Z"),
        access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
        accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST),
        creatorId = null,
        receiptMode = Conversation.ReceiptMode.DISABLED,
        messageTimer = null,
        userMessageTimer = null,
        archived = false,
        archivedDateTime = null,
        mlsVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
        legalHoldStatus = Conversation.LegalHoldStatus.DISABLED
    )

    fun self(
        protocolInfo: ProtocolInfo = ProtocolInfo.Proteus,
        teamId: TeamId = TeamId("teamId")
    ) = Conversation(
        id = ID.copy(value = "SELF ID"),
        name = "SELF Name",
        type = Conversation.Type.Self,
        teamId = teamId,
        protocol = protocolInfo,
        mutedStatus = MutedConversationStatus.AllAllowed,
        removedBy = null,
        lastNotificationDate = null,
        lastModifiedDate = null,
        lastReadDate = Instant.parse("2022-03-30T15:36:00.000Z"),
        access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
        accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST),
        creatorId = null,
        receiptMode = Conversation.ReceiptMode.DISABLED,
        messageTimer = null,
        userMessageTimer = null,
        archived = false,
        archivedDateTime = null,
        mlsVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
        legalHoldStatus = Conversation.LegalHoldStatus.DISABLED
    )

    fun group(
        id: ConversationId = ID,
        protocolInfo: ProtocolInfo = ProtocolInfo.Proteus,
        teamId: TeamId = TeamId("teamId")
    ) = Conversation(
        id = id,
        name = "GROUP Name",
        type = Conversation.Type.Group.Regular,
        teamId = teamId,
        protocol = protocolInfo,
        mutedStatus = MutedConversationStatus.AllAllowed,
        removedBy = null,
        lastNotificationDate = null,
        lastModifiedDate = Instant.parse("2022-03-30T15:36:00.000Z"),
        lastReadDate = Instant.parse("2022-03-30T15:36:00.000Z"),
        access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
        accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST),
        creatorId = "someValue",
        receiptMode = Conversation.ReceiptMode.DISABLED,
        messageTimer = null,
        userMessageTimer = null,
        archived = false,
        archivedDateTime = null,
        mlsVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
        legalHoldStatus = Conversation.LegalHoldStatus.DISABLED
    )

    fun channel(
        protocolInfo: ProtocolInfo = ProtocolInfo.Proteus
    ) = group(protocolInfo = protocolInfo).copy(type = Conversation.Type.Group.Channel)

    fun entity(
        id: ConversationIDEntity = ENTITY_ID,
    ) = ConversationEntity(
        id,
        "convo name",
        ConversationEntity.Type.SELF,
        "teamId",
        ConversationEntity.ProtocolInfo.Proteus,
        creatorId = "someValue",
        lastNotificationDate = null,
        lastModifiedDate = Instant.parse("2022-03-30T15:36:00.000Z"),
        lastReadDate = Instant.parse("2022-03-30T15:36:00.000Z"),
        access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
        accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
        receiptMode = ConversationEntity.ReceiptMode.DISABLED,
        messageTimer = null,
        userMessageTimer = null,
        archived = false,
        archivedInstant = null,
        mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
        channelAccess = null,
        channelAddPermission = null,
        wireCell = null,
        historySharingRetentionSeconds = 0,
    )

    fun groupViewEntity(
        protocolInfo: ConversationEntity.ProtocolInfo = ConversationEntity.ProtocolInfo.Proteus,
        teamId: TeamId = TeamId("teamId")
    ) = ConversationViewEntity(
        id = ENTITY_ID,
        name = "GROUP Name",
        type = ConversationEntity.Type.GROUP,
        previewAssetId = null,
        mutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED,
        teamId = teamId.value,
        lastModifiedDate = Instant.parse("2022-03-30T15:36:00.000Z"),
        lastReadDate = Instant.parse("2022-03-30T15:36:00.000Z"),
        userAvailabilityStatus = null,
        userType = null,
        botService = null,
        userDeleted = false,
        connectionStatus = null,
        otherUserId = null,
        lastNotificationDate = null,
        protocolInfo = protocolInfo,
        creatorId = "someValue",
        accessList = listOf(ConversationEntity.Access.CODE, ConversationEntity.Access.INVITE),
        accessRoleList = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.GUEST),
        protocol = ConversationEntity.Protocol.MLS,
        mlsCipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256,
        mlsEpoch = 0L,
        mlsGroupId = null,
        mlsLastKeyingMaterialUpdateDate = Instant.UNIX_FIRST_DATE,
        mlsGroupState = ConversationEntity.GroupState.ESTABLISHED,
        mlsProposalTimer = null,
        mutedTime = 0L,
        removedBy = null,
        selfRole = MemberEntity.Role.Member,
        receiptMode = ConversationEntity.ReceiptMode.DISABLED,
        messageTimer = null,
        userMessageTimer = null,
        userDefederated = null,
        archived = false,
        archivedDateTime = null,
        mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        userSupportedProtocols = null,
        userActiveOneOnOneConversationId = null,
        legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
        accentId = null,
        isFavorite = false,
        folderId = null,
        folderName = null,
        channelAccess = null,
        channelAddPermission = null,
        wireCell = null,
        historySharingRetentionSeconds = 0,
    )

    val NETWORK_ID = QualifiedID("valueConversation", "domainConversation")
    val USER_1 = UserId("member1", "domainMember")
    val MEMBER_TEST1 = Member(USER_1, Member.Role.Admin)
    val USER_2 = UserId("member2", "domainMember")
    val MEMBER_TEST2 = Member(USER_2, Member.Role.Member)
    val NETWORK_USER_ID1 =
        com.wire.kalium.network.api.model.UserId(value = "member1", domain = "domainMember")
    val NETWORK_USER_ID2 =
        com.wire.kalium.network.api.model.UserId(value = "member2", domain = "domainMember")
    val USER_ID1 = UserId(value = "member1", domain = "domainMember")

    val CONVERSATION_RESPONSE = ConversationResponse(
        "creator",
        ConversationMembersResponse(
            ConversationMemberDTO.Self(QualifiedID("41d2b365-f4a9-4ba1-bddf-5afb8aca6786", "domain"), "wire_admin"),
            listOf(ConversationMemberDTO.Other(QualifiedID("otherValue", "domain"), conversationRole = "wire_member"))
        ),
        "Group Name",
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
        mlsCipherSuiteTag = null,
        receiptMode = ReceiptMode.DISABLED,
    )

    val CONVERSATION_RESPONSE_DTO = ConversationResponseDTO(
        conversationsFound = listOf(CONVERSATION_RESPONSE),
        conversationsFailed = listOf(QualifiedID("failedId", "someDomain")),
        conversationsNotFound = emptyList()
    )

    val ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE =
        ConversationMemberAddedResponse.Changed(
            EventContentDTO.Conversation.MemberJoinDTO(
                NETWORK_ID,
                NETWORK_USER_ID1,
                Instant.UNIX_FIRST_DATE,
                ConversationMembers(emptyList(), emptyList()),
                NETWORK_ID.value
            )
        )

    val ADD_SERVICE_TO_CONVERSATION_SUCCESSFUL_RESPONSE =
        ServiceAddedResponse.Changed(
            EventContentDTO.Conversation.MemberJoinDTO(
                NETWORK_ID,
                NETWORK_USER_ID1,
                Instant.UNIX_FIRST_DATE,
                ConversationMembers(emptyList(), emptyList()),
                NETWORK_ID.value
            )
        )

    val REMOVE_MEMBER_FROM_CONVERSATION_SUCCESSFUL_RESPONSE =
        ConversationMemberRemovedResponse.Changed(
            EventContentDTO.Conversation.MemberLeaveDTO(
                NETWORK_ID,
                NETWORK_USER_ID1,
                Instant.UNIX_FIRST_DATE,
                ConversationMemberRemovedDTO(emptyList(), MemberLeaveReasonDTO.LEFT),
                NETWORK_USER_ID1.value
            )
        )

    val ENTITY_GROUP = entity().copy(type = ConversationEntity.Type.GROUP)

    val VIEW_ENTITY = ConversationViewEntity(
        id = ENTITY_ID,
        name = "convo name",
        type = ConversationEntity.Type.SELF,
        previewAssetId = null,
        mutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED,
        teamId = "teamId",
        lastModifiedDate = Instant.parse("2022-03-30T15:36:00.000Z"),
        lastReadDate = Instant.parse("2022-03-30T15:36:00.000Z"),
        userAvailabilityStatus = null,
        userType = null,
        botService = null,
        userDeleted = false,
        connectionStatus = null,
        otherUserId = null,
        lastNotificationDate = null,
        protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
        creatorId = "someValue",
        accessList = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
        accessRoleList = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
        protocol = ConversationEntity.Protocol.MLS,
        mlsCipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256,
        mlsEpoch = 0L,
        mlsGroupId = null,
        mlsLastKeyingMaterialUpdateDate = Instant.UNIX_FIRST_DATE,
        mlsGroupState = ConversationEntity.GroupState.ESTABLISHED,
        mlsProposalTimer = null,
        mutedTime = 0L,
        removedBy = null,
        selfRole = MemberEntity.Role.Member,
        receiptMode = ConversationEntity.ReceiptMode.DISABLED,
        messageTimer = null,
        userMessageTimer = null,
        userDefederated = null,
        archived = false,
        archivedDateTime = null,
        mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        userSupportedProtocols = null,
        userActiveOneOnOneConversationId = null,
        legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
        accentId = null,
        isFavorite = false,
        folderId = null,
        folderName = null,
        channelAccess = null,
        channelAddPermission = null,
        wireCell = null,
        historySharingRetentionSeconds = 0,
    )

    val VIEW_ONE_ON_ONE = VIEW_ENTITY.copy(
        type = ConversationEntity.Type.ONE_ON_ONE,
    )

    val MLS_PROTOCOL_INFO = ProtocolInfo.MLS(
        GROUP_ID,
        ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
        0UL,
        Instant.parse("2021-03-30T15:36:00.000Z"),
        cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
    )

    val PROTEUS_PROTOCOL_INFO = ProtocolInfo.Proteus

    val MIXED_PROTOCOL_INFO = ProtocolInfo.Mixed(
        GROUP_ID,
        ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
        0UL,
        Instant.parse("2021-03-30T15:36:00.000Z"),
        cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
    )

    val CONVERSATION = oneOnOne(
        id = ConversationId("conv_id", "domain"),
        teamId = TeamId("Some-team")
    )

    val MLS_CONVERSATION = oneOnOne(
        protocolInfo = MLS_PROTOCOL_INFO,
        id = ConversationId("conv_id", "domain"),
        teamId = TeamId("Some-team")
    ).copy(name = "MLS Name")

    val CONVERSATION_CODE_INFO: ConversationCodeInfo = ConversationCodeInfo("conv_id_value", "name")
    val MIXED_CONVERSATION = MLS_CONVERSATION.copy(
        protocol = MIXED_PROTOCOL_INFO
    )
}
