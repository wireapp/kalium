package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.base.authenticated.conversation.MemberUpdateDTO
import com.wire.kalium.network.api.base.authenticated.conversation.MutedStatus
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.datetime.Instant

interface ConversationStatusMapper {
    fun toMutedStatusApiModel(mutedStatus: MutedConversationStatus, mutedStatusTimestamp: Long): MemberUpdateDTO
    fun toMutedStatusDaoModel(mutedStatus: MutedConversationStatus): ConversationEntity.MutedStatus
    fun fromMutedStatusDaoModel(mutedStatus: ConversationEntity.MutedStatus): MutedConversationStatus
    fun fromMutedStatusApiToDaoModel(mutedStatus: MutedStatus?): ConversationEntity.MutedStatus
    fun fromRemovedByToLogicModel(removedBy: UserIDEntity): UserId
}

class ConversationStatusMapperImpl(val idMapper: IdMapper) : ConversationStatusMapper {
    override fun toMutedStatusApiModel(mutedStatus: MutedConversationStatus, mutedStatusTimestamp: Long): MemberUpdateDTO {
        return MemberUpdateDTO(
            otrMutedStatus = MutedStatus.fromOrdinal(mutedStatus.status),
            otrMutedRef = Instant.fromEpochMilliseconds(mutedStatusTimestamp).toString()
        )
    }

    override fun toMutedStatusDaoModel(mutedStatus: MutedConversationStatus): ConversationEntity.MutedStatus {
        return when (mutedStatus) {
            MutedConversationStatus.AllAllowed -> ConversationEntity.MutedStatus.ALL_ALLOWED
            MutedConversationStatus.OnlyMentionsAllowed -> ConversationEntity.MutedStatus.ONLY_MENTIONS_ALLOWED
            MutedConversationStatus.AllMuted -> ConversationEntity.MutedStatus.ALL_MUTED
            else -> ConversationEntity.MutedStatus.ALL_ALLOWED
        }
    }

    override fun fromMutedStatusDaoModel(mutedStatus: ConversationEntity.MutedStatus): MutedConversationStatus {
        return when (mutedStatus) {
            ConversationEntity.MutedStatus.ALL_ALLOWED -> MutedConversationStatus.AllAllowed
            ConversationEntity.MutedStatus.ONLY_MENTIONS_ALLOWED -> MutedConversationStatus.OnlyMentionsAllowed
            ConversationEntity.MutedStatus.ALL_MUTED -> MutedConversationStatus.AllMuted
            else -> MutedConversationStatus.AllAllowed
        }
    }

    override fun fromMutedStatusApiToDaoModel(mutedStatus: MutedStatus?): ConversationEntity.MutedStatus {
        return when (mutedStatus) {
            MutedStatus.ALL_ALLOWED -> ConversationEntity.MutedStatus.ALL_ALLOWED
            MutedStatus.ONLY_MENTIONS_ALLOWED -> ConversationEntity.MutedStatus.ONLY_MENTIONS_ALLOWED
            MutedStatus.ALL_MUTED -> ConversationEntity.MutedStatus.ALL_MUTED
            else -> ConversationEntity.MutedStatus.ALL_ALLOWED
        }
    }

    override fun fromRemovedByToLogicModel(removedBy: UserIDEntity): UserId = idMapper.fromDaoModel(removedBy)

}
