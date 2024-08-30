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
@file:Suppress("TooManyFunctions")

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.cryptography.E2EIConversationState
import com.wire.kalium.logic.data.connection.ConnectionStatusMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.NetworkQualifiedId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.MessagePreview
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.BotService
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.toModel
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConvTeamInfo
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.CreateConversationRequest
import com.wire.kalium.network.api.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity.GroupState
import com.wire.kalium.persistence.dao.conversation.ConversationEntity.Protocol
import com.wire.kalium.persistence.dao.conversation.ConversationEntity.ProtocolInfo
import com.wire.kalium.persistence.dao.conversation.ConversationViewEntity
import com.wire.kalium.persistence.dao.conversation.ProposalTimerEntity
import com.wire.kalium.persistence.util.requireField
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.time.DurationUnit
import kotlin.time.toDuration

interface ConversationMapper {
    fun fromApiModelToDaoModel(apiModel: ConversationResponse, mlsGroupState: GroupState?, selfUserTeamId: TeamId?): ConversationEntity
    fun fromDaoModel(daoModel: ConversationViewEntity): Conversation
    fun fromDaoModel(daoModel: ConversationEntity): Conversation
    fun fromDaoModelToDetails(
        daoModel: ConversationViewEntity,
        lastMessage: MessagePreview?,
        unreadEventCount: UnreadEventCount?
    ): ConversationDetails

    fun fromDaoModel(daoModel: ProposalTimerEntity): ProposalTimer
    fun toDAOAccess(accessList: Set<ConversationAccessDTO>): List<ConversationEntity.Access>
    fun toDAOAccessRole(accessRoleList: Set<ConversationAccessRoleDTO>): List<ConversationEntity.AccessRole>
    fun toDAOGroupState(groupState: Conversation.ProtocolInfo.MLSCapable.GroupState): GroupState
    fun toDAOProposalTimer(proposalTimer: ProposalTimer): ProposalTimerEntity
    fun toApiModel(access: Conversation.Access): ConversationAccessDTO
    fun toApiModel(accessRole: Conversation.AccessRole): ConversationAccessRoleDTO
    fun toApiModel(protocol: ConversationOptions.Protocol): ConvProtocol
    fun toApiModel(name: String?, members: List<UserId>, teamId: String?, options: ConversationOptions): CreateConversationRequest

    fun fromMigrationModel(conversation: Conversation): ConversationEntity
    fun fromFailedGroupConversationToEntity(conversationId: NetworkQualifiedId): ConversationEntity
    fun verificationStatusToEntity(verificationStatus: Conversation.VerificationStatus): ConversationEntity.VerificationStatus
    fun verificationStatusFromEntity(verificationStatus: ConversationEntity.VerificationStatus): Conversation.VerificationStatus
    fun legalHoldStatusToEntity(legalHoldStatus: Conversation.LegalHoldStatus): ConversationEntity.LegalHoldStatus
    fun legalHoldStatusFromEntity(legalHoldStatus: ConversationEntity.LegalHoldStatus): Conversation.LegalHoldStatus

    fun fromConversationEntityType(type: ConversationEntity.Type): Conversation.Type

    fun fromModelToDAOAccess(accessList: Set<Conversation.Access>): List<ConversationEntity.Access>
    fun fromModelToDAOAccessRole(accessRoleList: Set<Conversation.AccessRole>): List<ConversationEntity.AccessRole>
    fun fromApiModelToAccessModel(accessList: Set<ConversationAccessDTO>): Set<Conversation.Access>
    fun fromApiModelToAccessRoleModel(accessRoleList: Set<ConversationAccessRoleDTO>): Set<Conversation.AccessRole>
}

@Suppress("TooManyFunctions", "LongParameterList")
internal class ConversationMapperImpl(
    private val selfUserId: UserId,
    private val idMapper: IdMapper,
    private val conversationStatusMapper: ConversationStatusMapper,
    private val protocolInfoMapper: ProtocolInfoMapper,
    private val userAvailabilityStatusMapper: AvailabilityStatusMapper,
    private val domainUserTypeMapper: DomainUserTypeMapper,
    private val connectionStatusMapper: ConnectionStatusMapper,
    private val conversationRoleMapper: ConversationRoleMapper,
    private val receiptModeMapper: ReceiptModeMapper = MapperProvider.receiptModeMapper()
) : ConversationMapper {

    override fun fromApiModelToDaoModel(
        apiModel: ConversationResponse,
        mlsGroupState: GroupState?,
        selfUserTeamId: TeamId?
    ): ConversationEntity = ConversationEntity(
        id = idMapper.fromApiToDao(apiModel.id),
        name = apiModel.name,
        type = apiModel.toConversationType(selfUserTeamId),
        teamId = apiModel.teamId,
        protocolInfo = apiModel.getProtocolInfo(mlsGroupState),
        mutedStatus = conversationStatusMapper.fromMutedStatusApiToDaoModel(apiModel.members.self.otrMutedStatus),
        mutedTime = apiModel.members.self.otrMutedRef?.let { Instant.parse(it) }?.toEpochMilliseconds() ?: 0,
        removedBy = null,
        creatorId = apiModel.creator ?: selfUserId.value, // NOTE mls 1-1 does not have the creator field set.
        lastReadDate = Instant.UNIX_FIRST_DATE,
        lastNotificationDate = null,
        lastModifiedDate = apiModel.lastEventTime.toInstant(),
        access = apiModel.access.map { it.toDAO() },
        accessRole = (apiModel.accessRole ?: ConversationAccessRoleDTO.DEFAULT_VALUE_WHEN_NULL)
            .map { it.toDAO() },
        receiptMode = receiptModeMapper.fromApiToDaoModel(apiModel.receiptMode),
        messageTimer = apiModel.messageTimer,
        userMessageTimer = null, // user picked self deletion timer is only persisted locally
        hasIncompleteMetadata = false,
        archived = apiModel.members.self.otrArchived ?: false,
        archivedInstant = apiModel.members.self.otrArchivedRef?.toInstant(),
        mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED
    )

    override fun fromDaoModel(daoModel: ConversationViewEntity): Conversation = with(daoModel) {
        val lastReadDateEntity = if (type == ConversationEntity.Type.CONNECTION_PENDING) Instant.UNIX_FIRST_DATE
        else lastReadDate

        Conversation(
            id = id.toModel(),
            name = name,
            type = type.fromDaoModelToType(),
            teamId = teamId?.let { TeamId(it) },
            protocol = protocolInfoMapper.fromEntity(protocolInfo),
            mutedStatus = conversationStatusMapper.fromMutedStatusDaoModel(mutedStatus),
            removedBy = removedBy?.let { conversationStatusMapper.fromRemovedByToLogicModel(it) },
            lastNotificationDate = lastNotificationDate,
            lastModifiedDate = lastModifiedDate,
            lastReadDate = lastReadDateEntity,
            access = accessList.map { it.toDAO() },
            accessRole = accessRoleList.map { it.toDAO() },
            creatorId = creatorId,
            receiptMode = receiptModeMapper.fromEntityToModel(receiptMode),
            messageTimer = messageTimer?.toDuration(DurationUnit.MILLISECONDS),
            userMessageTimer = userMessageTimer?.toDuration(DurationUnit.MILLISECONDS),
            archived = archived,
            archivedDateTime = archivedDateTime,
            mlsVerificationStatus = verificationStatusFromEntity(mlsVerificationStatus),
            proteusVerificationStatus = verificationStatusFromEntity(proteusVerificationStatus),
            legalHoldStatus = legalHoldStatusFromEntity(legalHoldStatus)
        )
    }

    override fun fromDaoModel(daoModel: ConversationEntity): Conversation = with(daoModel) {
        val lastReadDateEntity = if (type == ConversationEntity.Type.CONNECTION_PENDING) Instant.UNIX_FIRST_DATE
        else lastReadDate
        Conversation(
            id = id.toModel(),
            name = name,
            type = type.fromDaoModelToType(),
            teamId = teamId?.let { TeamId(it) },
            protocol = protocolInfoMapper.fromEntity(protocolInfo),
            mutedStatus = conversationStatusMapper.fromMutedStatusDaoModel(mutedStatus),
            removedBy = removedBy?.let { conversationStatusMapper.fromRemovedByToLogicModel(it) },
            lastNotificationDate = lastNotificationDate,
            lastModifiedDate = lastModifiedDate,
            lastReadDate = lastReadDateEntity,
            access = access.map { it.toDAO() },
            accessRole = accessRole.map { it.toDAO() },
            creatorId = creatorId,
            receiptMode = receiptModeMapper.fromEntityToModel(receiptMode),
            messageTimer = messageTimer?.toDuration(DurationUnit.MILLISECONDS),
            userMessageTimer = userMessageTimer?.toDuration(DurationUnit.MILLISECONDS),
            archived = archived,
            archivedDateTime = archivedInstant,
            mlsVerificationStatus = verificationStatusFromEntity(mlsVerificationStatus),
            proteusVerificationStatus = verificationStatusFromEntity(proteusVerificationStatus),
            legalHoldStatus = legalHoldStatusFromEntity(legalHoldStatus)
        )
    }

    @Suppress("ComplexMethod", "LongMethod")
    override fun fromDaoModelToDetails(
        daoModel: ConversationViewEntity,
        lastMessage: MessagePreview?,
        unreadEventCount: UnreadEventCount?
    ): ConversationDetails =
        with(daoModel) {
            when (type) {
                ConversationEntity.Type.SELF -> {
                    ConversationDetails.Self(fromDaoModel(daoModel))
                }

                ConversationEntity.Type.ONE_ON_ONE -> {
                    ConversationDetails.OneOne(
                        conversation = fromDaoModel(daoModel),
                        otherUser = OtherUser(
                            id = otherUserId.requireField("otherUserID in OneOnOne").toModel(),
                            name = name,
                            accentId = 0,
                            userType = domainUserTypeMapper.fromUserTypeEntity(userType),
                            availabilityStatus = userAvailabilityStatusMapper.fromDaoAvailabilityStatusToModel(userAvailabilityStatus),
                            deleted = userDeleted ?: false,
                            botService = botService?.let { BotService(it.id, it.provider) },
                            handle = null,
                            completePicture = previewAssetId?.toModel(),
                            previewPicture = previewAssetId?.toModel(),
                            teamId = teamId?.let { TeamId(it) },
                            connectionStatus = connectionStatusMapper.fromDaoModel(connectionStatus),
                            expiresAt = null,
                            defederated = userDefederated ?: false,
                            isProteusVerified = false,
                            supportedProtocols = userSupportedProtocols?.map { it.toModel() }?.toSet(),
                            activeOneOnOneConversationId = userActiveOneOnOneConversationId?.toModel()
                        ),
                        userType = domainUserTypeMapper.fromUserTypeEntity(userType),
                        unreadEventCount = unreadEventCount ?: mapOf(),
                        lastMessage = lastMessage
                    )
                }

                ConversationEntity.Type.GROUP -> {
                    ConversationDetails.Group(
                        conversation = fromDaoModel(daoModel),
                        hasOngoingCall = callStatus != null, // todo: we can do better!
                        unreadEventCount = unreadEventCount ?: mapOf(),
                        lastMessage = lastMessage,
                        isSelfUserMember = isMember,
                        isSelfUserCreator = isCreator == 1L,
                        selfRole = selfRole?.let { conversationRoleMapper.fromDAO(it) }
                    )
                }

                ConversationEntity.Type.CONNECTION_PENDING -> {
                    val otherUser = OtherUser(
                        id = otherUserId.requireField("otherUserID in OneOnOne").toModel(),
                        name = name,
                        accentId = 0,
                        userType = domainUserTypeMapper.fromUserTypeEntity(userType),
                        availabilityStatus = userAvailabilityStatusMapper.fromDaoAvailabilityStatusToModel(userAvailabilityStatus),
                        deleted = userDeleted ?: false,
                        botService = botService?.let { BotService(it.id, it.provider) },
                        handle = null,
                        completePicture = previewAssetId?.toModel(),
                        previewPicture = previewAssetId?.toModel(),
                        teamId = teamId?.let { TeamId(it) },
                        expiresAt = null,
                        defederated = userDefederated ?: false,
                        isProteusVerified = false,
                        supportedProtocols = userSupportedProtocols?.map { it.toModel() }?.toSet()
                    )

                    ConversationDetails.Connection(
                        conversationId = id.toModel(),
                        otherUser = otherUser,
                        userType = domainUserTypeMapper.fromUserTypeEntity(userType),
                        lastModifiedDate = lastModifiedDate ?: Instant.UNIX_FIRST_DATE,
                        connection = Connection(
                            conversationId = id.value,
                            from = "",
                            lastUpdate = Instant.UNIX_FIRST_DATE,
                            qualifiedConversationId = id.toModel(),
                            qualifiedToId = otherUserId.requireField("otherUserID in Connection").toModel(),
                            status = connectionStatusMapper.fromDaoModel(connectionStatus),
                            toId = "", // todo
                            fromUser = otherUser
                        ),
                        protocolInfo = protocolInfoMapper.fromEntity(protocolInfo),
                        access = accessList.map { it.toDAO() },
                        accessRole = accessRoleList.map { it.toDAO() },
                    )
                }
            }
        }

    override fun fromDaoModel(daoModel: ProposalTimerEntity): ProposalTimer =
        ProposalTimer(idMapper.fromGroupIDEntity(daoModel.groupID), daoModel.firingDate)

    override fun toDAOAccess(accessList: Set<ConversationAccessDTO>): List<ConversationEntity.Access> = accessList.map {
        when (it) {
            ConversationAccessDTO.PRIVATE -> ConversationEntity.Access.PRIVATE
            ConversationAccessDTO.CODE -> ConversationEntity.Access.CODE
            ConversationAccessDTO.INVITE -> ConversationEntity.Access.INVITE
            ConversationAccessDTO.SELF_INVITE -> ConversationEntity.Access.SELF_INVITE
            ConversationAccessDTO.LINK -> ConversationEntity.Access.LINK
        }
    }

    override fun toDAOAccessRole(accessRoleList: Set<ConversationAccessRoleDTO>): List<ConversationEntity.AccessRole> =
        accessRoleList.map {
            when (it) {
                ConversationAccessRoleDTO.TEAM_MEMBER -> ConversationEntity.AccessRole.TEAM_MEMBER
                ConversationAccessRoleDTO.NON_TEAM_MEMBER -> ConversationEntity.AccessRole.NON_TEAM_MEMBER
                ConversationAccessRoleDTO.GUEST -> ConversationEntity.AccessRole.GUEST
                ConversationAccessRoleDTO.SERVICE -> ConversationEntity.AccessRole.SERVICE
                ConversationAccessRoleDTO.EXTERNAL -> ConversationEntity.AccessRole.EXTERNAL
            }
        }

    override fun toDAOGroupState(groupState: Conversation.ProtocolInfo.MLSCapable.GroupState): GroupState =
        when (groupState) {
            Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED -> GroupState.ESTABLISHED
            Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN -> GroupState.PENDING_JOIN
            Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_WELCOME_MESSAGE -> GroupState.PENDING_WELCOME_MESSAGE
            Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_CREATION -> GroupState.PENDING_CREATION
        }

    override fun toDAOProposalTimer(proposalTimer: ProposalTimer): ProposalTimerEntity =
        ProposalTimerEntity(idMapper.toGroupIDEntity(proposalTimer.groupID), proposalTimer.timestamp)

    override fun toApiModel(
        name: String?,
        members: List<UserId>,
        teamId: String?,
        options: ConversationOptions
    ) = CreateConversationRequest(
        qualifiedUsers = if (options.protocol == ConversationOptions.Protocol.PROTEUS)
            members.map { it.toApi() }
        else
            emptyList(),
        name = name,
        access = options.access?.toList()?.map { toApiModel(it) },
        accessRole = options.accessRole?.map { toApiModel(it) },
        convTeamInfo = teamId?.let { ConvTeamInfo(false, it) },
        messageTimer = null,
        receiptMode = if (options.readReceiptsEnabled) ReceiptMode.ENABLED else ReceiptMode.DISABLED,
        conversationRole = ConversationDataSource.DEFAULT_MEMBER_ROLE,
        protocol = toApiModel(options.protocol),
        creatorClient = options.creatorClientId?.value
    )

    override fun toApiModel(access: Conversation.Access): ConversationAccessDTO = when (access) {
        Conversation.Access.PRIVATE -> ConversationAccessDTO.PRIVATE
        Conversation.Access.CODE -> ConversationAccessDTO.CODE
        Conversation.Access.INVITE -> ConversationAccessDTO.INVITE
        Conversation.Access.SELF_INVITE -> ConversationAccessDTO.SELF_INVITE
        Conversation.Access.LINK -> ConversationAccessDTO.LINK
    }

    override fun toApiModel(accessRole: Conversation.AccessRole): ConversationAccessRoleDTO = when (accessRole) {
        Conversation.AccessRole.TEAM_MEMBER -> ConversationAccessRoleDTO.TEAM_MEMBER
        Conversation.AccessRole.NON_TEAM_MEMBER -> ConversationAccessRoleDTO.NON_TEAM_MEMBER
        Conversation.AccessRole.GUEST -> ConversationAccessRoleDTO.GUEST
        Conversation.AccessRole.SERVICE -> ConversationAccessRoleDTO.SERVICE
        Conversation.AccessRole.EXTERNAL -> ConversationAccessRoleDTO.EXTERNAL
    }

    override fun toApiModel(protocol: ConversationOptions.Protocol): ConvProtocol = when (protocol) {
        ConversationOptions.Protocol.PROTEUS -> ConvProtocol.PROTEUS
        ConversationOptions.Protocol.MLS -> ConvProtocol.MLS
    }

    override fun fromMigrationModel(conversation: Conversation): ConversationEntity = with(conversation) {
        ConversationEntity(
            id = conversation.id.toDao(),
            name = name,
            type = type.toDAO(),
            teamId = conversation.teamId?.value,
            protocolInfo = protocolInfoMapper.toEntity(conversation.protocol),
            mutedStatus = conversationStatusMapper.toMutedStatusDaoModel(conversation.mutedStatus),
            mutedTime = 0,
            removedBy = null,
            creatorId = creatorId.orEmpty(),
            lastNotificationDate = conversation.lastNotificationDate,
            lastModifiedDate = conversation.lastModifiedDate ?: Instant.UNIX_FIRST_DATE,
            lastReadDate = conversation.lastReadDate,
            access = conversation.access.map { it.toDAO() },
            accessRole = conversation.accessRole.map { it.toDAO() },
            receiptMode = receiptModeMapper.toDaoModel(conversation.receiptMode),
            messageTimer = messageTimer?.inWholeMilliseconds,
            userMessageTimer = userMessageTimer?.inWholeMilliseconds,
            archived = archived,
            archivedInstant = archivedDateTime,
            mlsVerificationStatus = verificationStatusToEntity(mlsVerificationStatus),
            proteusVerificationStatus = verificationStatusToEntity(proteusVerificationStatus),
            legalHoldStatus = legalHoldStatusToEntity(legalHoldStatus)
        )
    }

    /**
     * Default values and marked as [ConversationEntity.hasIncompleteMetadata] = true.
     * So later we can re-fetch them.
     */
    override fun fromFailedGroupConversationToEntity(conversationId: NetworkQualifiedId): ConversationEntity = ConversationEntity(
        id = conversationId.toDao(),
        name = null,
        type = ConversationEntity.Type.GROUP,
        teamId = null,
        protocolInfo = ProtocolInfo.Proteus,
        mutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED,
        mutedTime = 0,
        removedBy = null,
        creatorId = "",
        lastNotificationDate = "1970-01-01T00:00:00.000Z".toInstant(),
        lastModifiedDate = "1970-01-01T00:00:00.000Z".toInstant(),
        lastReadDate = "1970-01-01T00:00:00.000Z".toInstant(),
        access = emptyList(),
        accessRole = emptyList(),
        receiptMode = ConversationEntity.ReceiptMode.DISABLED,
        messageTimer = null,
        userMessageTimer = null,
        hasIncompleteMetadata = true,
        archived = false,
        archivedInstant = null,
        mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED
    )

    private fun ConversationResponse.getProtocolInfo(mlsGroupState: GroupState?): ProtocolInfo {
        return when (protocol) {
            ConvProtocol.MLS -> ProtocolInfo.MLS(
                groupId ?: "",
                mlsGroupState ?: GroupState.PENDING_JOIN,
                epoch ?: 0UL,
                keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                ConversationEntity.CipherSuite.fromTag(mlsCipherSuiteTag)
            )

            ConvProtocol.MIXED -> ProtocolInfo.Mixed(
                groupId ?: "",
                mlsGroupState ?: GroupState.PENDING_JOIN,
                epoch ?: 0UL,
                keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                ConversationEntity.CipherSuite.fromTag(mlsCipherSuiteTag)
            )

            ConvProtocol.PROTEUS -> ProtocolInfo.Proteus
        }
    }

    override fun verificationStatusFromEntity(verificationStatus: ConversationEntity.VerificationStatus) =
        Conversation.VerificationStatus.valueOf(verificationStatus.name)

    override fun verificationStatusToEntity(verificationStatus: Conversation.VerificationStatus) =
        ConversationEntity.VerificationStatus.valueOf(verificationStatus.name)

    override fun legalHoldStatusToEntity(legalHoldStatus: Conversation.LegalHoldStatus): ConversationEntity.LegalHoldStatus =
        when (legalHoldStatus) {
            Conversation.LegalHoldStatus.ENABLED -> ConversationEntity.LegalHoldStatus.ENABLED
            Conversation.LegalHoldStatus.DEGRADED -> ConversationEntity.LegalHoldStatus.DEGRADED
            else -> ConversationEntity.LegalHoldStatus.DISABLED
        }

    override fun legalHoldStatusFromEntity(legalHoldStatus: ConversationEntity.LegalHoldStatus): Conversation.LegalHoldStatus =
        when (legalHoldStatus) {
            ConversationEntity.LegalHoldStatus.ENABLED -> Conversation.LegalHoldStatus.ENABLED
            ConversationEntity.LegalHoldStatus.DEGRADED -> Conversation.LegalHoldStatus.DEGRADED
            ConversationEntity.LegalHoldStatus.DISABLED -> Conversation.LegalHoldStatus.DISABLED
        }

    override fun fromConversationEntityType(type: ConversationEntity.Type): Conversation.Type {
        return type.fromDaoModelToType()
    }

    override fun fromModelToDAOAccess(accessList: Set<Conversation.Access>): List<ConversationEntity.Access> =
        accessList.map { it.toDAO() }

    override fun fromModelToDAOAccessRole(accessRoleList: Set<Conversation.AccessRole>): List<ConversationEntity.AccessRole> =
        accessRoleList.map { it.toDAO() }

    override fun fromApiModelToAccessModel(accessList: Set<ConversationAccessDTO>): Set<Conversation.Access> =
        accessList.map { it.toModel() }.toSet()

    override fun fromApiModelToAccessRoleModel(accessRoleList: Set<ConversationAccessRoleDTO>): Set<Conversation.AccessRole> =
        accessRoleList.map { it.toModel() }.toSet()
}

internal fun ConversationResponse.toConversationType(selfUserTeamId: TeamId?): ConversationEntity.Type {
    return when (type) {
        ConversationResponse.Type.SELF -> ConversationEntity.Type.SELF
        ConversationResponse.Type.GROUP -> {
            // Fake team 1:1 conversations
            val onlyOneOtherMember = members.otherMembers.size == 1
            val noCustomName = name.isNullOrBlank()
            val belongsToSelfTeam = selfUserTeamId != null && selfUserTeamId.value == teamId
            val isTeamOneOne = onlyOneOtherMember && noCustomName && belongsToSelfTeam
            if (isTeamOneOne) {
                ConversationEntity.Type.ONE_ON_ONE
            } else {
                ConversationEntity.Type.GROUP
            }
        }

        ConversationResponse.Type.ONE_TO_ONE -> ConversationEntity.Type.ONE_ON_ONE
        ConversationResponse.Type.WAIT_FOR_CONNECTION -> ConversationEntity.Type.CONNECTION_PENDING
    }
}

private fun ConversationEntity.Type.fromDaoModelToType(): Conversation.Type = when (this) {
    ConversationEntity.Type.SELF -> Conversation.Type.SELF
    ConversationEntity.Type.ONE_ON_ONE -> Conversation.Type.ONE_ON_ONE
    ConversationEntity.Type.GROUP -> Conversation.Type.GROUP
    ConversationEntity.Type.CONNECTION_PENDING -> Conversation.Type.CONNECTION_PENDING
}

private fun ConversationAccessRoleDTO.toDAO(): ConversationEntity.AccessRole = when (this) {
    ConversationAccessRoleDTO.TEAM_MEMBER -> ConversationEntity.AccessRole.TEAM_MEMBER
    ConversationAccessRoleDTO.NON_TEAM_MEMBER -> ConversationEntity.AccessRole.NON_TEAM_MEMBER
    ConversationAccessRoleDTO.GUEST -> ConversationEntity.AccessRole.GUEST
    ConversationAccessRoleDTO.SERVICE -> ConversationEntity.AccessRole.SERVICE
    ConversationAccessRoleDTO.EXTERNAL -> ConversationEntity.AccessRole.EXTERNAL
}

private fun ConversationAccessDTO.toDAO(): ConversationEntity.Access = when (this) {
    ConversationAccessDTO.PRIVATE -> ConversationEntity.Access.PRIVATE
    ConversationAccessDTO.CODE -> ConversationEntity.Access.CODE
    ConversationAccessDTO.INVITE -> ConversationEntity.Access.INVITE
    ConversationAccessDTO.SELF_INVITE -> ConversationEntity.Access.SELF_INVITE
    ConversationAccessDTO.LINK -> ConversationEntity.Access.LINK
}

private fun ConversationEntity.Access.toDAO(): Conversation.Access = when (this) {
    ConversationEntity.Access.PRIVATE -> Conversation.Access.PRIVATE
    ConversationEntity.Access.INVITE -> Conversation.Access.INVITE
    ConversationEntity.Access.SELF_INVITE -> Conversation.Access.SELF_INVITE
    ConversationEntity.Access.LINK -> Conversation.Access.LINK
    ConversationEntity.Access.CODE -> Conversation.Access.CODE
}

private fun ConversationEntity.AccessRole.toDAO(): Conversation.AccessRole = when (this) {
    ConversationEntity.AccessRole.TEAM_MEMBER -> Conversation.AccessRole.TEAM_MEMBER
    ConversationEntity.AccessRole.NON_TEAM_MEMBER -> Conversation.AccessRole.NON_TEAM_MEMBER
    ConversationEntity.AccessRole.GUEST -> Conversation.AccessRole.GUEST
    ConversationEntity.AccessRole.SERVICE -> Conversation.AccessRole.SERVICE
    ConversationEntity.AccessRole.EXTERNAL -> Conversation.AccessRole.EXTERNAL
}

internal fun Conversation.ProtocolInfo.MLSCapable.GroupState.toDao(): ConversationEntity.GroupState = when (this) {
    Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED -> GroupState.ESTABLISHED
    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_CREATION -> GroupState.PENDING_CREATION
    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN -> GroupState.PENDING_JOIN
    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_WELCOME_MESSAGE -> GroupState.PENDING_WELCOME_MESSAGE
}

internal fun Conversation.Type.toDAO(): ConversationEntity.Type = when (this) {
    Conversation.Type.SELF -> ConversationEntity.Type.SELF
    Conversation.Type.ONE_ON_ONE -> ConversationEntity.Type.ONE_ON_ONE
    Conversation.Type.GROUP -> ConversationEntity.Type.GROUP
    Conversation.Type.CONNECTION_PENDING -> ConversationEntity.Type.CONNECTION_PENDING
}

private fun Conversation.AccessRole.toDAO(): ConversationEntity.AccessRole = when (this) {
    Conversation.AccessRole.TEAM_MEMBER -> ConversationEntity.AccessRole.TEAM_MEMBER
    Conversation.AccessRole.NON_TEAM_MEMBER -> ConversationEntity.AccessRole.NON_TEAM_MEMBER
    Conversation.AccessRole.GUEST -> ConversationEntity.AccessRole.GUEST
    Conversation.AccessRole.SERVICE -> ConversationEntity.AccessRole.SERVICE
    Conversation.AccessRole.EXTERNAL -> ConversationEntity.AccessRole.EXTERNAL
}

private fun Conversation.Access.toDAO(): ConversationEntity.Access = when (this) {
    Conversation.Access.PRIVATE -> ConversationEntity.Access.PRIVATE
    Conversation.Access.INVITE -> ConversationEntity.Access.INVITE
    Conversation.Access.SELF_INVITE -> ConversationEntity.Access.SELF_INVITE
    Conversation.Access.LINK -> ConversationEntity.Access.LINK
    Conversation.Access.CODE -> ConversationEntity.Access.CODE
}

private fun ConversationAccessDTO.toModel(): Conversation.Access = when (this) {
    ConversationAccessDTO.PRIVATE -> Conversation.Access.PRIVATE
    ConversationAccessDTO.CODE -> Conversation.Access.CODE
    ConversationAccessDTO.INVITE -> Conversation.Access.INVITE
    ConversationAccessDTO.SELF_INVITE -> Conversation.Access.SELF_INVITE
    ConversationAccessDTO.LINK -> Conversation.Access.LINK
}

private fun ConversationAccessRoleDTO.toModel(): Conversation.AccessRole = when (this) {
    ConversationAccessRoleDTO.TEAM_MEMBER -> Conversation.AccessRole.TEAM_MEMBER
    ConversationAccessRoleDTO.NON_TEAM_MEMBER -> Conversation.AccessRole.NON_TEAM_MEMBER
    ConversationAccessRoleDTO.GUEST -> Conversation.AccessRole.GUEST
    ConversationAccessRoleDTO.SERVICE -> Conversation.AccessRole.SERVICE
    ConversationAccessRoleDTO.EXTERNAL -> Conversation.AccessRole.EXTERNAL
}

internal fun Conversation.Protocol.toApi(): ConvProtocol = when (this) {
    Conversation.Protocol.PROTEUS -> ConvProtocol.PROTEUS
    Conversation.Protocol.MIXED -> ConvProtocol.MIXED
    Conversation.Protocol.MLS -> ConvProtocol.MLS
}

internal fun Conversation.Protocol.toDao(): Protocol = when (this) {
    Conversation.Protocol.PROTEUS -> Protocol.PROTEUS
    Conversation.Protocol.MIXED -> Protocol.MIXED
    Conversation.Protocol.MLS -> Protocol.MLS
}

internal fun ConvProtocol.toModel(): Conversation.Protocol = when (this) {
    ConvProtocol.PROTEUS -> Conversation.Protocol.PROTEUS
    ConvProtocol.MIXED -> Conversation.Protocol.MIXED
    ConvProtocol.MLS -> Conversation.Protocol.MLS
}

internal fun Protocol.toModel(): Conversation.Protocol = when (this) {
    Protocol.PROTEUS -> Conversation.Protocol.PROTEUS
    Protocol.MIXED -> Conversation.Protocol.MIXED
    Protocol.MLS -> Conversation.Protocol.MLS
}

internal fun E2EIConversationState.toModel(): Conversation.VerificationStatus = when (this) {
    E2EIConversationState.VERIFIED -> Conversation.VerificationStatus.VERIFIED
    E2EIConversationState.NOT_VERIFIED -> Conversation.VerificationStatus.NOT_VERIFIED
    E2EIConversationState.NOT_ENABLED -> Conversation.VerificationStatus.NOT_VERIFIED
}

internal fun ConversationEntity.VerificationStatus.toModel(): Conversation.VerificationStatus = when (this) {
    ConversationEntity.VerificationStatus.VERIFIED -> Conversation.VerificationStatus.VERIFIED
    ConversationEntity.VerificationStatus.NOT_VERIFIED -> Conversation.VerificationStatus.NOT_VERIFIED
    ConversationEntity.VerificationStatus.DEGRADED -> Conversation.VerificationStatus.DEGRADED
}
