/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.persistence.SelectConversationByMember
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.datetime.Instant
import com.wire.kalium.persistence.ConversationDetails as SQLDelightConversationView
internal class ConversationMapper {
    fun toModel(conversation: SQLDelightConversationView): ConversationViewEntity = with(conversation) {
        ConversationViewEntity(
            id = qualifiedId,
            name = name,
            type = type,
            teamId = teamId,
            protocolInfo = mapProtocolInfo(
                protocol,
                mls_group_id,
                mls_group_state,
                mls_epoch,
                mls_last_keying_material_update_date,
                mls_cipher_suite
            ),
            isCreator = isCreator,
            mutedStatus = mutedStatus,
            mutedTime = muted_time,
            creatorId = creator_id,
            lastNotificationDate = lastNotifiedMessageDate,
            lastModifiedDate = last_modified_date,
            lastReadDate = lastReadDate,
            accessList = access_list,
            accessRoleList = access_role_list,
            protocol = protocol,
            mlsCipherSuite = mls_cipher_suite,
            mlsEpoch = mls_epoch,
            mlsGroupId = mls_group_id,
            mlsLastKeyingMaterialUpdateDate = mls_last_keying_material_update_date,
            mlsGroupState = mls_group_state,
            mlsProposalTimer = mls_proposal_timer,
            callStatus = callStatus,
            previewAssetId = previewAssetId,
            userAvailabilityStatus = userAvailabilityStatus,
            userType = userType,
            botService = botService,
            userDeleted = userDeleted,
            connectionStatus = connectionStatus,
            otherUserId = otherUserId,
            selfRole = selfRole,
            receiptMode = receipt_mode,
            messageTimer = message_timer,
            userMessageTimer = user_message_timer,
            userSupportedProtocols = userSupportedProtocols
        )
    }

    @Suppress("LongParameterList")
    fun toModel(
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
        messageTimer: Long?,
        userMessageTimer: Long?,
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
    )

    fun fromOneToOneToModel(conversation: SelectConversationByMember?): ConversationViewEntity? {
        return conversation?.run {
            ConversationViewEntity(
                id = qualifiedId,
                name = name,
                type = type,
                teamId = teamId,
                protocolInfo = mapProtocolInfo(
                    protocol,
                    mls_group_id,
                    mls_group_state,
                    mls_epoch,
                    mls_last_keying_material_update_date,
                    mls_cipher_suite
                ),
                isCreator = isCreator,
                mutedStatus = mutedStatus,
                mutedTime = muted_time,
                creatorId = creator_id,
                lastNotificationDate = lastNotifiedMessageDate,
                lastModifiedDate = last_modified_date,
                lastReadDate = lastReadDate,
                accessList = access_list,
                accessRoleList = access_role_list,
                protocol = protocol,
                mlsCipherSuite = mls_cipher_suite,
                mlsEpoch = mls_epoch,
                mlsGroupId = mls_group_id,
                mlsLastKeyingMaterialUpdateDate = mls_last_keying_material_update_date,
                mlsGroupState = mls_group_state,
                mlsProposalTimer = mls_proposal_timer,
                callStatus = callStatus,
                previewAssetId = previewAssetId,
                userAvailabilityStatus = userAvailabilityStatus,
                userType = userType,
                botService = botService,
                userDeleted = userDeleted,
                connectionStatus = connectionStatus,
                otherUserId = otherUserId,
                selfRole = selfRole,
                receiptMode = receipt_mode,
                messageTimer = message_timer,
                userMessageTimer = user_message_timer,
                userSupportedProtocols = userSupportedProtocols
            )
        }
    }

    @Suppress("LongParameterList")
    fun mapProtocolInfo(
        protocol: ConversationEntity.Protocol,
        mlsGroupId: String?,
        mlsGroupState: ConversationEntity.GroupState,
        mlsEpoch: Long,
        mlsLastKeyingMaterialUpdate: Instant,
        mlsCipherSuite: ConversationEntity.CipherSuite,
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
}
