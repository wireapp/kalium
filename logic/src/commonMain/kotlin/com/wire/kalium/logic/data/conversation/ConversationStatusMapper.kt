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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.base.authenticated.conversation.MemberUpdateDTO
import com.wire.kalium.network.api.base.authenticated.conversation.MutedStatus
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString

interface ConversationStatusMapper {
    fun toMutedStatusApiModel(mutedStatus: MutedConversationStatus, mutedStatusTimestamp: Long): MemberUpdateDTO
    fun toMutedStatusDaoModel(mutedStatus: MutedConversationStatus): ConversationEntity.MutedStatus
    fun fromMutedStatusDaoModel(mutedStatus: ConversationEntity.MutedStatus): MutedConversationStatus
    fun fromMutedStatusApiToDaoModel(mutedStatus: MutedStatus?): ConversationEntity.MutedStatus
    fun fromRemovedByToLogicModel(removedBy: UserIDEntity): UserId
    fun toArchivedStatusApiModel(isArchived: Boolean, archivedStatusTimestamp: Long): MemberUpdateDTO
}

class ConversationStatusMapperImpl(val idMapper: IdMapper) : ConversationStatusMapper {
    override fun toMutedStatusApiModel(mutedStatus: MutedConversationStatus, mutedStatusTimestamp: Long): MemberUpdateDTO {
        return MemberUpdateDTO(
            otrMutedStatus = MutedStatus.fromOrdinal(mutedStatus.status),
            otrMutedRef = mutedStatusTimestamp.toIsoDateTimeString()
        )
    }

    override fun toMutedStatusDaoModel(mutedStatus: MutedConversationStatus): ConversationEntity.MutedStatus {
        return when (mutedStatus) {
            MutedConversationStatus.AllAllowed -> ConversationEntity.MutedStatus.ALL_ALLOWED
            MutedConversationStatus.OnlyMentionsAndRepliesAllowed -> ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED
            MutedConversationStatus.AllMuted -> ConversationEntity.MutedStatus.ALL_MUTED
        }
    }

    override fun fromMutedStatusDaoModel(mutedStatus: ConversationEntity.MutedStatus): MutedConversationStatus {
        return when (mutedStatus) {
            ConversationEntity.MutedStatus.ALL_ALLOWED -> MutedConversationStatus.AllAllowed
            ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED -> MutedConversationStatus.OnlyMentionsAndRepliesAllowed
            ConversationEntity.MutedStatus.ALL_MUTED -> MutedConversationStatus.AllMuted
            else -> MutedConversationStatus.AllAllowed
        }
    }

    override fun fromMutedStatusApiToDaoModel(mutedStatus: MutedStatus?): ConversationEntity.MutedStatus {
        return when (mutedStatus) {
            MutedStatus.ALL_ALLOWED -> ConversationEntity.MutedStatus.ALL_ALLOWED
            MutedStatus.ONLY_MENTIONS_ALLOWED -> ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED
            MutedStatus.ALL_MUTED -> ConversationEntity.MutedStatus.ALL_MUTED
            else -> ConversationEntity.MutedStatus.ALL_ALLOWED
        }
    }

    override fun fromRemovedByToLogicModel(removedBy: UserIDEntity): UserId = removedBy.toModel()
    override fun toArchivedStatusApiModel(
        isArchived: Boolean,
        archivedStatusTimestamp: Long
    ): MemberUpdateDTO = MemberUpdateDTO(
        otrArchived = isArchived,
        otrArchivedRef = archivedStatusTimestamp.toIsoDateTimeString()
    )
}
