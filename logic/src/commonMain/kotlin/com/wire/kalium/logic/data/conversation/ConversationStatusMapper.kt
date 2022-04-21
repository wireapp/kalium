package com.wire.kalium.logic.data.conversation

import com.wire.kalium.network.api.conversation.MemberUpdateDTO
import com.wire.kalium.network.api.conversation.MutedStatus
import com.wire.kalium.persistence.dao.ConversationEntity
import kotlinx.datetime.Instant

interface ConversationStatusMapper {
    fun toApiModel(mutedStatus: MutedConversationStatus, mutedStatusTimestamp: Long): MemberUpdateDTO
    fun toDaoModel(mutedStatus: MutedConversationStatus): ConversationEntity.MutedStatus
    fun fromDaoModel(mutedStatus: ConversationEntity.MutedStatus): MutedConversationStatus
    fun fromApiToDaoModel(mutedStatus: MutedStatus?): ConversationEntity.MutedStatus
}

class ConversationStatusMapperImpl : ConversationStatusMapper {
    override fun toApiModel(mutedStatus: MutedConversationStatus, mutedStatusTimestamp: Long): MemberUpdateDTO {
        return MemberUpdateDTO(
            otrMutedStatus = MutedStatus.fromOrdinal(mutedStatus.status),
            otrMutedRef = Instant.fromEpochMilliseconds(mutedStatusTimestamp).toString()
        )
    }

    override fun toDaoModel(mutedStatus: MutedConversationStatus): ConversationEntity.MutedStatus {
        return when (mutedStatus) {
            MutedConversationStatus.AllAllowed -> ConversationEntity.MutedStatus.ALL_ALLOWED
            MutedConversationStatus.OnlyMentionsAllowed -> ConversationEntity.MutedStatus.ONLY_MENTIONS_ALLOWED
            MutedConversationStatus.AllMuted -> ConversationEntity.MutedStatus.ALL_MUTED
            else -> ConversationEntity.MutedStatus.ALL_ALLOWED
        }
    }

    override fun fromDaoModel(mutedStatus: ConversationEntity.MutedStatus): MutedConversationStatus {
        return when (mutedStatus) {
            ConversationEntity.MutedStatus.ALL_ALLOWED -> MutedConversationStatus.AllAllowed
            ConversationEntity.MutedStatus.ONLY_MENTIONS_ALLOWED -> MutedConversationStatus.OnlyMentionsAllowed
            ConversationEntity.MutedStatus.ALL_MUTED -> MutedConversationStatus.AllMuted
            else -> MutedConversationStatus.AllAllowed
        }
    }

    override fun fromApiToDaoModel(mutedStatus: MutedStatus?): ConversationEntity.MutedStatus {
        return when (mutedStatus) {
            MutedStatus.ALL_ALLOWED -> ConversationEntity.MutedStatus.ALL_ALLOWED
            MutedStatus.ONLY_MENTIONS_ALLOWED -> ConversationEntity.MutedStatus.ONLY_MENTIONS_ALLOWED
            MutedStatus.ALL_MUTED -> ConversationEntity.MutedStatus.ALL_MUTED
            else -> ConversationEntity.MutedStatus.ALL_ALLOWED
        }
    }

}
