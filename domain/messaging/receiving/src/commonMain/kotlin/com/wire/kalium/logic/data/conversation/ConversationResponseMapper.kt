/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.conversation.ChannelAddPermissionTypeDTO
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationHistorySettingsDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.cellEnabled
import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity.GroupState
import com.wire.kalium.persistence.dao.conversation.ConversationEntity.ProtocolInfo
import com.wire.kalium.persistence.dao.conversation.ConversationEntity.Type.Unknown
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import kotlin.time.Instant

/** The app and extension map backend conversation metadata identically. */
class ConversationResponseMapper(
    private val selfUserId: UserId,
    private val idMapper: IdMapper = IdMapper(),
    private val conversationStatusMapper: ConversationStatusMapper = ConversationStatusMapperImpl(idMapper),
    private val receiptModeMapper: ReceiptModeMapper = ReceiptModeMapperImpl(idMapper),
) {
    fun fromApiModelToDaoModel(
        apiModel: ConversationResponse,
        mlsGroupState: GroupState?,
        selfUserTeamId: TeamId?,
    ): ConversationEntity {
        val conversationId = idMapper.fromApiToDao(apiModel.id)
        val type = apiModel.toConversationType(selfUserTeamId)
        return ConversationEntity(
            id = conversationId,
            name = apiModel.name,
            type = type,
            teamId = apiModel.teamId,
            protocolInfo = apiModel.getProtocolInfo(mlsGroupState),
            mutedStatus = conversationStatusMapper.fromMutedStatusApiToDaoModel(apiModel.members.self?.otrMutedStatus),
            mutedTime = apiModel.members.self?.otrMutedRef?.let { Instant.parse(it) }?.toEpochMilliseconds() ?: 0,
            removedBy = null,
            creatorId = apiModel.creator ?: selfUserId.value, // NOTE mls 1-1 does not have the creator field set.
            lastReadDate = Instant.UNIX_FIRST_DATE,
            lastNotificationDate = null,
            lastModifiedDate = Instant.parse(apiModel.lastEventTime),
            access = apiModel.access.map { it.toDAO() },
            accessRole = (apiModel.accessRole ?: ConversationAccessRoleDTO.DEFAULT_VALUE_WHEN_NULL)
                .map { it.toDAO() },
            receiptMode = receiptModeMapper.fromApiToDaoModel(apiModel.receiptMode),
            messageTimer = apiModel.messageTimer,
            userMessageTimer = null, // user picked self deletion timer is only persisted locally
            hasIncompleteMetadata = false,
            archived = apiModel.members.self?.otrArchived ?: false,
            archivedInstant = apiModel.members.self?.otrArchivedRef?.let { Instant.parse(it) },
            mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
            proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
            legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
            channelAccess = null, // TODO: implement when api is ready
            channelAddPermission = apiModel.channelAddUserPermissionTypeDTO?.toDAO(),
            wireCell = conversationId.toString().takeIf { apiModel.cellEnabled() }, // TODO refactor to boolean in WPB-16946
            historySharingRetentionSeconds = when (val historySharingSettings = apiModel.historySharingSettings) {
                ConversationHistorySettingsDTO.Private -> 0
                is ConversationHistorySettingsDTO.ShareWithNewMembers -> historySharingSettings.depth.inWholeSeconds
            }
        )
    }

    private fun ConversationResponse.getProtocolInfo(mlsGroupState: GroupState?): ProtocolInfo {
        return when (protocol) {
            ConvProtocol.MLS -> ProtocolInfo.MLS(
                groupId = groupId ?: "",
                groupState = mlsGroupState ?: GroupState.PENDING_JOIN,
                epoch = epoch ?: 0UL,
                keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                cipherSuite = ConversationEntity.CipherSuite.fromTag(mlsCipherSuiteTag)
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

}

fun ConversationResponse.toConversationType(selfUserTeamId: TeamId?): ConversationEntity.Type {
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
                when (val groupType = conversationGroupType) {
                    is ConversationResponse.GroupType.Channel -> ConversationEntity.Type.CHANNEL
                    is ConversationResponse.GroupType.Meeting -> ConversationEntity.Type.MEETING
                    is ConversationResponse.GroupType.Unknown -> Unknown(groupType.value)
                    ConversationResponse.GroupType.RegularGroup -> ConversationEntity.Type.GROUP
                    // is not one in one and have no group type, we can assume it is a regular group
                    null -> ConversationEntity.Type.GROUP
                }
            }
        }

        ConversationResponse.Type.ONE_TO_ONE -> ConversationEntity.Type.ONE_ON_ONE
        ConversationResponse.Type.WAIT_FOR_CONNECTION -> ConversationEntity.Type.CONNECTION_PENDING
    }
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

private fun ChannelAddPermissionTypeDTO.toDAO(): ConversationEntity.ChannelAddPermission = when (this) {
    ChannelAddPermissionTypeDTO.ADMINS -> ConversationEntity.ChannelAddPermission.ADMINS
    ChannelAddPermissionTypeDTO.EVERYONE -> ConversationEntity.ChannelAddPermission.EVERYONE
}
