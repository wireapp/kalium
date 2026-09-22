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

import com.wire.kalium.logic.data.connection.ConnectionStatusMapper
import com.wire.kalium.logic.data.conversation.ConversationDetails.Group.Channel.ChannelAccess
import com.wire.kalium.logic.data.conversation.ConversationDetails.Group.Channel.ChannelAddPermission
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.NetworkQualifiedId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.MessageMapper
import com.wire.kalium.logic.data.message.MessagePreviewContent
import com.wire.kalium.logic.data.message.UnreadEventType
import com.wire.kalium.logic.data.mls.MLSPublicKeys
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.BotService
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.toModel
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.authenticated.conversation.ChannelAddPermissionTypeDTO
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConvTeamInfo
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.CreateConversationRequest
import com.wire.kalium.network.api.authenticated.conversation.GroupConversationType
import com.wire.kalium.network.api.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.authenticated.serverpublickey.MLSPublicKeysDTO
import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity.GroupState
import com.wire.kalium.persistence.dao.conversation.ConversationEntity.Protocol
import com.wire.kalium.persistence.dao.conversation.ConversationEntity.ProtocolInfo
import com.wire.kalium.persistence.dao.conversation.ConversationFilterEntity
import com.wire.kalium.persistence.dao.conversation.ConversationListItemEntity
import com.wire.kalium.persistence.dao.conversation.ConversationViewEntity
import com.wire.kalium.persistence.dao.conversation.ProposalTimerEntity
import com.wire.kalium.persistence.dao.unread.UnreadEventTypeEntity
import com.wire.kalium.persistence.util.requireField
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.Instant

internal interface ConversationMapper {
    fun fromApiModelToDaoModel(
        apiModel: ConversationResponse,
        mlsGroupState: GroupState?,
        selfUserTeamId: TeamId?,
    ): ConversationEntity

    fun fromApiModel(mlsPublicKeysDTO: MLSPublicKeysDTO?): MLSPublicKeys?
    fun fromDaoModel(daoModel: ConversationViewEntity): Conversation
    fun fromDaoModel(daoModel: ConversationEntity): Conversation
    fun fromDaoModelToDetails(daoModel: ConversationViewEntity): ConversationDetails
    fun fromListItemEntity(entity: ConversationListItemEntity): ConversationListItem
    fun fromDaoModelToDetailsWithEvents(daoModel: ConversationDetailsWithEventsEntity): ConversationDetailsWithEvents
    fun fromDaoModel(daoModel: ProposalTimerEntity): ProposalTimer
    fun toDAOAccess(accessList: Set<ConversationAccessDTO>): List<ConversationEntity.Access>
    fun toDAOAccessRole(accessRoleList: Set<ConversationAccessRoleDTO>): List<ConversationEntity.AccessRole>
    fun toDAOGroupState(groupState: Conversation.ProtocolInfo.MLSCapable.GroupState): GroupState
    fun toDAOProposalTimer(proposalTimer: ProposalTimer): ProposalTimerEntity
    fun toApiModel(access: Conversation.Access): ConversationAccessDTO
    fun toApiModel(accessRole: Conversation.AccessRole): ConversationAccessRoleDTO
    fun toApiModel(protocol: CreateConversationParam.Protocol): ConvProtocol
    fun toApiModel(name: String?, members: List<UserId>, teamId: String?, options: CreateConversationParam): CreateConversationRequest

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
    private val messageMapper: MessageMapper,
    private val receiptModeMapper: ReceiptModeMapper = MapperProvider.receiptModeMapper(),
) : ConversationMapper {

    override fun fromApiModelToDaoModel(
        apiModel: ConversationResponse,
        mlsGroupState: GroupState?,
        selfUserTeamId: TeamId?,
    ): ConversationEntity = ConversationResponseMapper(selfUserId, idMapper, conversationStatusMapper, receiptModeMapper)
        .fromApiModelToDaoModel(apiModel, mlsGroupState, selfUserTeamId)

    private fun fromConversationViewToEntity(daoModel: ConversationViewEntity): Conversation = with(daoModel) {
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

    override fun fromApiModel(mlsPublicKeysDTO: MLSPublicKeysDTO?) = mlsPublicKeysDTO?.let {
        MLSPublicKeys(
            removal = mlsPublicKeysDTO.removal
        )
    }

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
    override fun fromDaoModelToDetails(daoModel: ConversationViewEntity): ConversationDetails =
        with(daoModel) {
            when (val conversationType = type) {
                ConversationEntity.Type.SELF -> {
                    ConversationDetails.Self(fromConversationViewToEntity(daoModel))
                }

                ConversationEntity.Type.ONE_ON_ONE -> {
                    ConversationDetails.OneOne(
                        conversation = fromConversationViewToEntity(daoModel),
                        otherUser = OtherUser(
                            id = (otherUserId ?: id).toModel(),
                            name = name,
                            accentId = accentId ?: 0,
                            userType = domainUserTypeMapper.fromUserTypeEntity(userType),
                            availabilityStatus = userAvailabilityStatusMapper.fromDaoAvailabilityStatusToModel(userAvailabilityStatus),
                            deleted = userDeleted ?: (otherUserId == null),
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
                        isFavorite = isFavorite,
                        folder = folderId?.let { ConversationFolder(it, folderName ?: "", type = FolderType.USER) },
                    )
                }

                ConversationEntity.Type.CHANNEL -> ConversationDetails.Group.Channel(
                    conversation = fromConversationViewToEntity(daoModel),
                    isSelfUserMember = isMember,
                    selfRole = selfRole?.let { conversationRoleMapper.fromDAO(it) },
                    isFavorite = isFavorite,
                    folder = folderId?.let { ConversationFolder(it, folderName ?: "", type = FolderType.USER) },
                    access = channelAccess?.toModelChannelAccess() ?: ChannelAccess.PRIVATE,
                    permission = channelAddPermission?.toModelChannelPermission()
                        ?: ChannelAddPermission.ADMINS,
                    historySharing = daoModel.historySharingRetentionSeconds.takeIf { it > 0 }?.let {
                        ConversationHistorySettings.ShareWithNewMembers(it.seconds)
                    } ?: ConversationHistorySettings.Private,
                    wireCell = wireCell,
                )

                ConversationEntity.Type.MEETING -> ConversationDetails.Group.Meeting(
                    conversation = fromConversationViewToEntity(daoModel),
                    isSelfUserMember = isMember,
                    selfRole = selfRole?.let { conversationRoleMapper.fromDAO(it) },
                    isFavorite = isFavorite,
                    folder = folderId?.let { ConversationFolder(it, folderName ?: "", type = FolderType.USER) },
                    wireCell = wireCell,
                )

                ConversationEntity.Type.GROUP -> ConversationDetails.Group.Regular(
                    conversation = fromConversationViewToEntity(daoModel),
                    isSelfUserMember = isMember,
                    selfRole = selfRole?.let { conversationRoleMapper.fromDAO(it) },
                    isFavorite = isFavorite,
                    folder = folderId?.let { ConversationFolder(it, folderName ?: "", type = FolderType.USER) },
                    wireCell = wireCell,
                )

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

                is ConversationEntity.Type.Unknown -> throw IllegalArgumentException(
                    "Unexpected conversation type: ${conversationType.name}"
                )
            }
        }

    override fun fromListItemEntity(entity: ConversationListItemEntity): ConversationListItem = with(entity) {
        val id = position.conversationId.toModel()
        val time = position.activityTime
        val user = otherUser?.let {
            ConversationListUser(
                id = it.id.toModel(),
                name = it.name,
                avatarAssetId = it.avatarAssetId?.toModel(),
                accentId = it.accentId,
                availabilityStatus = userAvailabilityStatusMapper.fromDaoAvailabilityStatusToModel(it.availabilityStatus),
                userType = domainUserTypeMapper.fromUserTypeEntity(it.userType),
                connectionStatus = connectionStatusMapper.fromDaoModel(it.connectionStatus),
                deleted = it.deleted,
                defederated = it.defederated,
            )
        }
        val muted = conversationStatusMapper.fromMutedStatusDaoModel(mutedStatus)
        val legalHold = legalHoldStatusFromEntity(legalHoldStatus)
        val unread = ConversationListUnreadState(unreadMessages, unreadMentions, unreadReplies, unreadMissedCalls, unreadKnocks)
        val draftText = draftText
        val lastMessage = lastMessage
        val preview = when {
            draftText != null -> ConversationListPreview(MessagePreviewContent.Draft(draftText), isSelfMessage = true)
            lastMessage != null -> messageMapper.fromEntityToMessagePreview(lastMessage).let {
                ConversationListPreview(it.content, it.isSelfMessage)
            }
            else -> null
        }
        when (type) {
            ConversationEntity.Type.GROUP ->
                ConversationListItem.RegularGroup(id, time, name, isSelfUserMember, muted, legalHold, unread, preview)
            ConversationEntity.Type.CHANNEL -> ConversationListItem.Channel(
                id, time, name, isSelfUserMember,
                if (channelAccess == ConversationEntity.ChannelAccess.PUBLIC) ConversationListChannelAccess.PUBLIC
                else ConversationListChannelAccess.PRIVATE,
                muted, legalHold, unread, preview,
            )
            ConversationEntity.Type.ONE_ON_ONE ->
                ConversationListItem.OneOnOne(id, time, requireNotNull(user), muted, legalHold, unread, preview)
            ConversationEntity.Type.CONNECTION_PENDING -> ConversationListItem.PendingConnection(id, time, requireNotNull(user))
            else -> error("Unsupported conversation list type: $type")
        }
    }

    override fun fromDaoModelToDetailsWithEvents(daoModel: ConversationDetailsWithEventsEntity): ConversationDetailsWithEvents =
        ConversationDetailsWithEvents(
            conversationDetails = fromDaoModelToDetails(daoModel.conversationViewEntity),
            unreadEventCount = daoModel.unreadEvents.unreadEvents.mapKeys {
                when (it.key) {
                    UnreadEventTypeEntity.KNOCK -> UnreadEventType.KNOCK
                    UnreadEventTypeEntity.MISSED_CALL -> UnreadEventType.MISSED_CALL
                    UnreadEventTypeEntity.MENTION -> UnreadEventType.MENTION
                    UnreadEventTypeEntity.REPLY -> UnreadEventType.REPLY
                    UnreadEventTypeEntity.MESSAGE -> UnreadEventType.MESSAGE
                }
            },
            lastMessage = when {
                daoModel.conversationViewEntity.archived -> null // no last message in archived conversations
                daoModel.messageDraft != null -> messageMapper.fromDraftToMessagePreview(daoModel.messageDraft!!)
                daoModel.lastMessage != null -> messageMapper.fromEntityToMessagePreview(daoModel.lastMessage!!)
                else -> null
            },
            hasNewActivitiesToShow = daoModel.hasNewActivitiesToShow
        )

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
            Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_AFTER_RESET -> GroupState.PENDING_AFTER_RESET
        }

    override fun toDAOProposalTimer(proposalTimer: ProposalTimer): ProposalTimerEntity =
        ProposalTimerEntity(idMapper.toGroupIDEntity(proposalTimer.groupID), proposalTimer.timestamp)

    override fun toApiModel(
        name: String?,
        members: List<UserId>,
        teamId: String?,
        options: CreateConversationParam
    ) = CreateConversationRequest(
        qualifiedUsers = if (options.protocol == CreateConversationParam.Protocol.PROTEUS)
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
        creatorClient = options.creatorClientId?.value,
        groupConversationType = options.groupType.toApiModel(),
        channelAddPermissionTypeDTO = options.channelAddPermission.toApi(),
        cellEnabled = options.wireCellEnabled,
        skipCreator = options.skipCreator,
    )

    private fun CreateConversationParam.GroupType.toApiModel(): GroupConversationType = when (this) {
        CreateConversationParam.GroupType.REGULAR_GROUP -> GroupConversationType.REGULAR_GROUP
        CreateConversationParam.GroupType.CHANNEL -> GroupConversationType.CHANNEL
    }

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

    override fun toApiModel(protocol: CreateConversationParam.Protocol): ConvProtocol = when (protocol) {
        CreateConversationParam.Protocol.PROTEUS -> ConvProtocol.PROTEUS
        CreateConversationParam.Protocol.MLS -> ConvProtocol.MLS
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
            legalHoldStatus = legalHoldStatusToEntity(legalHoldStatus),
            channelAccess = null,
            channelAddPermission = null,
            wireCell = null,
            historySharingRetentionSeconds = 0, // There was no history sharing in old Android clients. So we can hard-code 0
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
        lastNotificationDate = Instant.parse("1970-01-01T00:00:00.000Z"),
        lastModifiedDate = Instant.parse("1970-01-01T00:00:00.000Z"),
        lastReadDate = Instant.parse("1970-01-01T00:00:00.000Z"),
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
        legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
        channelAccess = null,
        channelAddPermission = null,
        wireCell = null,
        historySharingRetentionSeconds = 0, // We can have no history sharing when they're failed.
    )

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

internal fun ConversationEntity.ChannelAddPermission.toModelChannelPermission(): ChannelAddPermission = when (this) {
    ConversationEntity.ChannelAddPermission.ADMINS -> ChannelAddPermission.ADMINS
    ConversationEntity.ChannelAddPermission.EVERYONE -> ChannelAddPermission.EVERYONE
}

internal fun ConversationEntity.ChannelAccess.toModelChannelAccess(): ChannelAccess = when (this) {
    ConversationEntity.ChannelAccess.PRIVATE -> ChannelAccess.PRIVATE
    ConversationEntity.ChannelAccess.PUBLIC -> ChannelAccess.PUBLIC
}

internal fun ConversationEntity.Type.fromDaoModelToType(): Conversation.Type = when (this) {
    ConversationEntity.Type.SELF -> Conversation.Type.Self
    ConversationEntity.Type.ONE_ON_ONE -> Conversation.Type.OneOnOne
    ConversationEntity.Type.GROUP -> Conversation.Type.Group.Regular
    ConversationEntity.Type.CHANNEL -> Conversation.Type.Group.Channel
    ConversationEntity.Type.MEETING -> Conversation.Type.Group.Meeting

    ConversationEntity.Type.CONNECTION_PENDING -> Conversation.Type.ConnectionPending
    is ConversationEntity.Type.Unknown -> throw IllegalArgumentException("Unexpected conversation type: $name")
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
    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_AFTER_RESET -> GroupState.PENDING_AFTER_RESET
}

internal fun Conversation.Type.toDAO(): ConversationEntity.Type = when (this) {
    Conversation.Type.Self -> ConversationEntity.Type.SELF
    Conversation.Type.OneOnOne -> ConversationEntity.Type.ONE_ON_ONE
    Conversation.Type.Group.Regular -> ConversationEntity.Type.GROUP
    Conversation.Type.Group.Channel -> ConversationEntity.Type.CHANNEL
    Conversation.Type.Group.Meeting -> ConversationEntity.Type.MEETING
    Conversation.Type.ConnectionPending -> ConversationEntity.Type.CONNECTION_PENDING
}

internal fun Conversation.Protocol.toApi(): ConvProtocol = when (this) {
    Conversation.Protocol.PROTEUS -> ConvProtocol.PROTEUS
    Conversation.Protocol.MIXED -> ConvProtocol.MIXED
    Conversation.Protocol.MLS -> ConvProtocol.MLS
}

internal fun ConvProtocol.toModel(): Conversation.Protocol = when (this) {
    ConvProtocol.PROTEUS -> Conversation.Protocol.PROTEUS
    ConvProtocol.MIXED -> Conversation.Protocol.MIXED
    ConvProtocol.MLS -> Conversation.Protocol.MLS
}

internal fun ChannelAddPermission.toApi(): ChannelAddPermissionTypeDTO = when (this) {
    ChannelAddPermission.ADMINS -> ChannelAddPermissionTypeDTO.ADMINS
    ChannelAddPermission.EVERYONE -> ChannelAddPermissionTypeDTO.EVERYONE
}

internal fun Protocol.toModel(): Conversation.Protocol = when (this) {
    Protocol.PROTEUS -> Conversation.Protocol.PROTEUS
    Protocol.MIXED -> Conversation.Protocol.MIXED
    Protocol.MLS -> Conversation.Protocol.MLS
}

internal fun ConversationEntity.VerificationStatus.toModel(): Conversation.VerificationStatus = when (this) {
    ConversationEntity.VerificationStatus.VERIFIED -> Conversation.VerificationStatus.VERIFIED
    ConversationEntity.VerificationStatus.NOT_VERIFIED -> Conversation.VerificationStatus.NOT_VERIFIED
    ConversationEntity.VerificationStatus.DEGRADED -> Conversation.VerificationStatus.DEGRADED
}

internal fun ConversationFilter.toDao(): ConversationFilterEntity = when (this) {
    ConversationFilter.All -> ConversationFilterEntity.ALL
    ConversationFilter.Favorites -> ConversationFilterEntity.FAVORITES
    ConversationFilter.Groups -> ConversationFilterEntity.GROUPS
    ConversationFilter.OneOnOne -> ConversationFilterEntity.ONE_ON_ONE
    ConversationFilter.Channels -> ConversationFilterEntity.CHANNELS
    is ConversationFilter.Folder -> ConversationFilterEntity.ALL // TODO think how to secure that
}
