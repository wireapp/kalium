/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.Member
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.conversation.ConversationRepositoryTest
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.toApi
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
import com.wire.kalium.network.api.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationCodeInfo
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.MemberLeaveReasonDTO
import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.api.model.ServiceAddedResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationViewEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant

object TestConversation {
    private const val conversationValue = "valueConvo"
    private const val conversationDomain = "domainConvo"

    val ID = ConversationId(conversationValue, conversationDomain)
    fun id(suffix: Int = 0) = ConversationId("${conversationValue}_$suffix", conversationDomain)

    fun ONE_ON_ONE(protocolInfo: ProtocolInfo = ProtocolInfo.Proteus) = Conversation(
        ID.copy(value = "1O1 ID"),
        "ONE_ON_ONE Name",
        Conversation.Type.ONE_ON_ONE,
        TestTeam.TEAM_ID,
        protocolInfo,
        MutedConversationStatus.AllAllowed,
        null,
        null,
        null,
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

    fun SELF(protocolInfo: ProtocolInfo = ProtocolInfo.Proteus) = Conversation(
        ID.copy(value = "SELF ID"),
        "SELF Name",
        Conversation.Type.SELF,
        TestTeam.TEAM_ID,
        protocolInfo,
        MutedConversationStatus.AllAllowed,
        null,
        null,
        null,
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

    fun GROUP_VIEW_ENTITY(protocolInfo: ConversationEntity.ProtocolInfo = ConversationEntity.ProtocolInfo.Proteus) = ConversationViewEntity(
        id = ENTITY_ID.copy(
            value = if (protocolInfo is ConversationEntity.ProtocolInfo.MLS) protocolInfo.groupId else "GROUP ID"
        ),
        name = "convo name",
        type = ConversationEntity.Type.GROUP,
        callStatus = null,
        previewAssetId = null,
        mutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED,
        teamId = "teamId",
        lastModifiedDate = "2022-03-30T15:36:00.000Z".toInstant(),
        lastReadDate = "2022-03-30T15:36:00.000Z".toInstant(),
        userAvailabilityStatus = null,
        userType = null,
        botService = null,
        userDeleted = false,
        connectionStatus = null,
        otherUserId = null,
        isCreator = 0L,
        lastNotificationDate = null,
        protocolInfo = protocolInfo,
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
            ConversationMemberDTO.Self(TestUser.SELF.id.toApi(), "wire_admin"),
            listOf(ConversationMemberDTO.Other(TestUser.OTHER.id.toApi(), conversationRole = "wire_member"))
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
        mlsCipherSuiteTag = null,
        receiptMode = ReceiptMode.DISABLED,
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

    val GROUP_ID = GroupID("mlsGroupId")
    val ENTITY_ID = QualifiedIDEntity(conversationValue, conversationDomain)
    val ENTITY = ConversationEntity(
        ENTITY_ID,
        "convo name",
        ConversationEntity.Type.SELF,
        "teamId",
        ConversationEntity.ProtocolInfo.Proteus,
        creatorId = "someValue",
        lastNotificationDate = null,
        lastModifiedDate = "2022-03-30T15:36:00.000Z".toInstant(),
        lastReadDate = "2022-03-30T15:36:00.000Z".toInstant(),
        access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
        accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
        receiptMode = ConversationEntity.ReceiptMode.DISABLED,
        messageTimer = null,
        userMessageTimer = null,
        archived = false,
        archivedInstant = null,
        mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED
    )
    val ENTITY_GROUP = ENTITY.copy(
        type = ConversationEntity.Type.GROUP
    )
    val VIEW_ENTITY = ConversationViewEntity(
        id = ENTITY_ID,
        name = "convo name",
        type = ConversationEntity.Type.SELF,
        callStatus = null,
        previewAssetId = null,
        mutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED,
        teamId = "teamId",
        lastModifiedDate = "2022-03-30T15:36:00.000Z".toInstant(),
        lastReadDate = "2022-03-30T15:36:00.000Z".toInstant(),
        userAvailabilityStatus = null,
        userType = null,
        botService = null,
        userDeleted = false,
        connectionStatus = null,
        otherUserId = null,
        isCreator = 0L,
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
        legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED
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
        lastReadDate = Instant.parse("2022-03-30T15:36:00.000Z"),
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

    val MLS_CONVERSATION = Conversation(
        ConversationId("conv_id", "domain"),
        "MLS Name",
        Conversation.Type.ONE_ON_ONE,
        TestTeam.TEAM_ID,
        MLS_PROTOCOL_INFO,
        MutedConversationStatus.AllAllowed,
        null,
        null,
        null,
        access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
        accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST),
        lastReadDate = Instant.parse("2022-03-30T15:36:00.000Z"),
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

    val CONVERSATION_CODE_INFO: ConversationCodeInfo = ConversationCodeInfo("conv_id_value", "name")
    val MIXED_CONVERSATION = MLS_CONVERSATION.copy(
        protocol = MIXED_PROTOCOL_INFO
    )
}
