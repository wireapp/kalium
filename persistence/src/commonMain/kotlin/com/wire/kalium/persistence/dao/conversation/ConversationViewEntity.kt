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
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.call.CallEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import kotlinx.datetime.Instant

@Suppress("FunctionParameterNaming")
data class ConversationViewEntity(
    val id: QualifiedIDEntity,
    val name: String?,
    val type: ConversationEntity.Type,
    val callStatus: CallEntity.Status?,
    val previewAssetId: QualifiedIDEntity?,
    val mutedStatus: ConversationEntity.MutedStatus,
    val teamId: String?,
    val lastModifiedDate: Instant?,
    val lastReadDate: Instant,
    val userAvailabilityStatus: UserAvailabilityStatusEntity?,
    val userType: UserTypeEntity?,
    val botService: BotIdEntity?,
    val userDeleted: Boolean?,
    val userDefederated: Boolean?,
    val connectionStatus: ConnectionEntity.State? = ConnectionEntity.State.NOT_CONNECTED,
    val otherUserId: QualifiedIDEntity?,
    val lastNotificationDate: Instant?,
    val selfRole: MemberEntity.Role?,
    val protocolInfo: ConversationEntity.ProtocolInfo,
    val accessList: List<ConversationEntity.Access>,
    val accessRoleList: List<ConversationEntity.AccessRole>,
    val protocol: ConversationEntity.Protocol,
    val mlsCipherSuite: ConversationEntity.CipherSuite,
    val mlsEpoch: Long,
    val mlsGroupId: String?,
    val mlsLastKeyingMaterialUpdateDate: Instant,
    val mlsGroupState: ConversationEntity.GroupState,
    val mlsProposalTimer: String?,
    val mutedTime: Long,
    val creatorId: String,
    val removedBy: UserIDEntity? = null, // TODO how to calculate?,
    val receiptMode: ConversationEntity.ReceiptMode,
    val messageTimer: Long?,
    val userMessageTimer: Long?,
    val archived: Boolean,
    val archivedDateTime: Instant?,
    val mlsVerificationStatus: ConversationEntity.VerificationStatus,
    val userSupportedProtocols: Set<SupportedProtocolEntity>?,
    val userActiveOneOnOneConversationId: ConversationIDEntity?,
    val proteusVerificationStatus: ConversationEntity.VerificationStatus,
    val legalHoldStatus: ConversationEntity.LegalHoldStatus,
    val accentId: Int?,
    val isFavorite: Boolean,
    val folderId: String?,
    val folderName: String?,
    val wireCell: String?,
) {
    val isMember: Boolean get() = selfRole != null
}
