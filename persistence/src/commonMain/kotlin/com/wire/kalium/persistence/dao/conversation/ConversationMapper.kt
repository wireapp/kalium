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
package com.wire.kalium.persistence.dao.conversation

import com.wire.kalium.persistence.dao.BotIdEntity
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.call.CallEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import kotlinx.datetime.Instant

data object ConversationMapper {
    // suppressed because the method cannot be shortened and there are unused parameters because sql view returns some duplicated fields
    @Suppress("LongParameterList", "LongMethod", "UnusedParameter")
    fun fromViewToModel(
        qualifiedId: QualifiedIDEntity,
        name: String?,
        type: ConversationEntity.Type,
        callStatus: CallEntity.Status?,
        previewAssetId: QualifiedIDEntity?,
        mutedStatus: ConversationEntity.MutedStatus,
        teamId: String?,
        lastModifiedDate: Instant?,
        lastReadDate: Instant,
        userAvailabilityStatus: UserAvailabilityStatusEntity?,
        userType: UserTypeEntity?,
        botService: BotIdEntity?,
        userDeleted: Boolean?,
        userDefederated: Boolean?,
        userSupportedProtocols: Set<SupportedProtocolEntity>?,
        connectionStatus: ConnectionEntity.State?,
        otherUserId: QualifiedIDEntity?,
        otherUserActiveConversationId: QualifiedIDEntity?,
        isActive: Long,
        accentId: Int?,
        lastNotifiedMessageDate: Instant?,
        selfRole: MemberEntity.Role?,
        protocol: ConversationEntity.Protocol,
        mlsCipherSuite: ConversationEntity.CipherSuite,
        mlsEpoch: Long,
        mlsGroupId: String?,
        mlsLastKeyingMaterialUpdateDate: Instant,
        mlsGroupState: ConversationEntity.GroupState,
        accessList: List<ConversationEntity.Access>,
        accessRoleList: List<ConversationEntity.AccessRole>,
        mlsProposalTimer: String?,
        mutedTime: Long,
        creatorId: String,
        receiptMode: ConversationEntity.ReceiptMode,
        messageTimer: Long?,
        userMessageTimer: Long?,
        incompleteMetadata: Boolean,
        archived: Boolean,
        archivedDateTime: Instant?,
        mlsVerificationStatus: ConversationEntity.VerificationStatus,
        proteusVerificationStatus: ConversationEntity.VerificationStatus,
        legalHoldStatus: ConversationEntity.LegalHoldStatus,
        isChannel: Boolean,
        channelAccess: ConversationEntity.ChannelAccess?,
        channelAddPermission: ConversationEntity.ChannelAddPermission?,
        selfUserId: QualifiedIDEntity?,
        interactionEnabled: Long,
        isFavorite: Boolean,
        folderId: String?,
        folderName: String?,
        wireCell: String?,
        historySharingRetentionSeconds: Long,
        deletedLocally: Boolean,
    ): ConversationViewEntity = ConversationViewEntity(
        id = qualifiedId,
        name = name,
        type = type,
        teamId = teamId,
        protocolInfo = mapProtocolInfo(
            protocol,
            mlsGroupId,
            mlsGroupState,
            mlsEpoch,
            mlsLastKeyingMaterialUpdateDate,
            mlsCipherSuite
        ),
        mutedStatus = mutedStatus,
        mutedTime = mutedTime,
        creatorId = creatorId,
        lastNotificationDate = lastNotifiedMessageDate,
        lastModifiedDate = lastModifiedDate,
        lastReadDate = lastReadDate,
        accessList = accessList,
        accessRoleList = accessRoleList,
        protocol = protocol,
        mlsCipherSuite = mlsCipherSuite,
        mlsEpoch = mlsEpoch,
        mlsGroupId = mlsGroupId,
        mlsLastKeyingMaterialUpdateDate = mlsLastKeyingMaterialUpdateDate,
        mlsGroupState = mlsGroupState,
        mlsProposalTimer = mlsProposalTimer,
        callStatus = callStatus,
        previewAssetId = previewAssetId,
        userAvailabilityStatus = userAvailabilityStatus,
        userType = userType,
        botService = botService,
        userDeleted = userDeleted,
        connectionStatus = connectionStatus,
        otherUserId = otherUserId,
        selfRole = selfRole,
        receiptMode = receiptMode,
        messageTimer = messageTimer,
        userMessageTimer = userMessageTimer,
        userDefederated = userDefederated,
        archived = archived,
        archivedDateTime = archivedDateTime,
        mlsVerificationStatus = mlsVerificationStatus,
        userSupportedProtocols = userSupportedProtocols,
        userActiveOneOnOneConversationId = otherUserActiveConversationId,
        proteusVerificationStatus = proteusVerificationStatus,
        legalHoldStatus = legalHoldStatus,
        accentId = accentId,
        isFavorite = isFavorite,
        folderId = folderId,
        folderName = folderName,
        isChannel = isChannel,
        channelAccess = channelAccess,
        channelAddPermission = channelAddPermission,
        wireCell = wireCell,
        historySharingRetentionSeconds = historySharingRetentionSeconds,
    )

    @Suppress("LongParameterList", "UnusedParameter")
    fun fromViewToModel(
        qualifiedId: QualifiedIDEntity,
        name: String?,
        type: ConversationEntity.Type,
        teamId: String?,
        mlsGroupId: String?,
        mlsGroupState: ConversationEntity.GroupState,
        mlsEpoch: Long,
        mlsProposalTimer: String?,
        protocol: ConversationEntity.Protocol,
        mutedStatus: ConversationEntity.MutedStatus,
        mutedTime: Long,
        creatorId: String,
        lastModifiedDate: Instant,
        lastNotifiedDate: Instant?,
        lastReadDate: Instant,
        accessList: List<ConversationEntity.Access>,
        accessRoleList: List<ConversationEntity.AccessRole>,
        mlsLastKeyingMaterialUpdateDate: Instant,
        mlsCipherSuite: ConversationEntity.CipherSuite,
        receiptMode: ConversationEntity.ReceiptMode,
        guestRoomLink: String?,
        messageTimer: Long?,
        userMessageTimer: Long?,
        incompleteMetadata: Boolean,
        mlsDegradedNotified: Boolean,
        isGuestPasswordProtected: Boolean,
        archived: Boolean,
        archivedDateTime: Instant?,
        verificationStatus: ConversationEntity.VerificationStatus,
        proteusVerificationStatus: ConversationEntity.VerificationStatus,
        degradedConversationNotified: Boolean,
        legalHoldStatus: ConversationEntity.LegalHoldStatus,
        isChannel: Boolean,
        channelAccess: ConversationEntity.ChannelAccess?,
        channelAddPermission: ConversationEntity.ChannelAddPermission?,
        wireCell: String?,
        deletedLocally: Boolean,
        historySharingRetentionSeconds: Long,
    ) = ConversationEntity(
        id = qualifiedId,
        name = name,
        type = type,
        teamId = teamId,
        protocolInfo = mapProtocolInfo(
            protocol,
            mlsGroupId,
            mlsGroupState,
            mlsEpoch,
            mlsLastKeyingMaterialUpdateDate,
            mlsCipherSuite
        ),
        mutedStatus = mutedStatus,
        mutedTime = mutedTime,
        creatorId = creatorId,
        lastNotificationDate = lastNotifiedDate,
        lastModifiedDate = lastModifiedDate,
        lastReadDate = lastReadDate,
        access = accessList,
        accessRole = accessRoleList,
        receiptMode = receiptMode,
        messageTimer = messageTimer,
        userMessageTimer = userMessageTimer,
        archived = archived,
        archivedInstant = archivedDateTime,
        mlsVerificationStatus = verificationStatus,
        proteusVerificationStatus = proteusVerificationStatus,
        legalHoldStatus = legalHoldStatus,
        isChannel = isChannel,
        channelAccess = channelAccess,
        channelAddPermission = channelAddPermission,
        wireCell = wireCell,
        historySharingRetentionSeconds = historySharingRetentionSeconds,
    )

    @Suppress("LongParameterList", "UnusedParameter", "FunctionParameterNaming")
    fun toConversationEntity(
        qualified_id: QualifiedIDEntity,
        name: String?,
        type: ConversationEntity.Type,
        team_id: String?,
        mls_group_id: String?,
        mls_group_state: ConversationEntity.GroupState,
        mls_epoch: Long,
        mls_proposal_timer: String?,
        protocol: ConversationEntity.Protocol,
        muted_status: ConversationEntity.MutedStatus,
        muted_time: Long,
        creator_id: String,
        last_modified_date: Instant,
        last_notified_date: Instant?,
        last_read_date: Instant,
        access_list: List<ConversationEntity.Access>,
        access_role_list: List<ConversationEntity.AccessRole>,
        mls_last_keying_material_update_date: Instant,
        mls_cipher_suite: ConversationEntity.CipherSuite,
        receipt_mode: ConversationEntity.ReceiptMode,
        guest_room_link: String?,
        message_timer: Long?,
        user_message_timer: Long?,
        incomplete_metadata: Boolean,
        mls_degraded_notified: Boolean,
        is_guest_password_protected: Boolean,
        archived: Boolean,
        archived_date_time: Instant?,
        verification_status: ConversationEntity.VerificationStatus,
        proteus_verification_status: ConversationEntity.VerificationStatus,
        degraded_conversation_notified: Boolean,
        legal_hold_status: ConversationEntity.LegalHoldStatus,
        is_channel: Boolean,
        channel_access: ConversationEntity.ChannelAccess?,
        channel_add_permission: ConversationEntity.ChannelAddPermission?,
        wireCell: String?,
        deleted_locally: Boolean,
        history_sharing_retention_seconds: Long,
    ) = ConversationEntity(
        id = qualified_id,
        name = name,
        type = type,
        teamId = team_id,
        protocolInfo = mapProtocolInfo(
            protocol,
            mls_group_id,
            mls_group_state,
            mls_epoch,
            mls_last_keying_material_update_date,
            mls_cipher_suite
        ),
        mutedStatus = muted_status,
        mutedTime = muted_time,
        creatorId = creator_id,
        lastNotificationDate = last_notified_date,
        lastModifiedDate = last_modified_date,
        lastReadDate = last_read_date,
        access = access_list,
        accessRole = access_role_list,
        receiptMode = receipt_mode,
        messageTimer = message_timer,
        userMessageTimer = user_message_timer,
        archived = archived,
        archivedInstant = archived_date_time,
        mlsVerificationStatus = verification_status,
        proteusVerificationStatus = proteus_verification_status,
        legalHoldStatus = legal_hold_status,
        isChannel = is_channel,
        channelAccess = channel_access,
        channelAddPermission = channel_add_permission,
        wireCell = wireCell,
        historySharingRetentionSeconds = history_sharing_retention_seconds
    )

    @Suppress("LongParameterList")
    fun mapProtocolInfo(
        protocol: ConversationEntity.Protocol,
        mlsGroupId: String?,
        mlsGroupState: ConversationEntity.GroupState,
        mlsEpoch: Long,
        mlsLastKeyingMaterialUpdate: Instant,
        mlsCipherSuite: ConversationEntity.CipherSuite
    ): ConversationEntity.ProtocolInfo {
        return when (protocol) {
            ConversationEntity.Protocol.MLS -> ConversationEntity.ProtocolInfo.MLS(
                mlsGroupId ?: "",
                mlsGroupState,
                mlsEpoch.toULong(),
                mlsLastKeyingMaterialUpdate,
                mlsCipherSuite
            )

            ConversationEntity.Protocol.MIXED -> ConversationEntity.ProtocolInfo.Mixed(
                mlsGroupId ?: "",
                mlsGroupState,
                mlsEpoch.toULong(),
                mlsLastKeyingMaterialUpdate,
                mlsCipherSuite
            )

            ConversationEntity.Protocol.PROTEUS -> ConversationEntity.ProtocolInfo.Proteus
        }
    }

    fun toE2EIConversationClient(
        mlsGroupId: String,
        userId: QualifiedIDEntity,
        clientId: String
    ) = E2EIConversationClientInfoEntity(userId, mlsGroupId, clientId)

}
