package com.wire.kalium.logic.data.conversation

import com.wire.kalium.network.api.conversation.MemberUpdateDTO
import com.wire.kalium.persistence.dao.ConversationEntity
import kotlinx.datetime.Instant

interface ConversationStatusMapper {
    fun toApiModel(mutedStatus: MutedConversationStatus, mutedStatusTimestamp: Long): MemberUpdateDTO
    fun toDaoModel(mutedStatus: MutedConversationStatus): ConversationEntity.MutedStatus
    fun fromDaoModel(mutedStatus: ConversationEntity.MutedStatus): MutedConversationStatus
    fun fromApiToDaoModel(mutedStatus: MemberUpdateDTO.MutedStatus?): ConversationEntity.MutedStatus
    fun fromApiToDaoModel(mutedStatus: Int?): ConversationEntity.MutedStatus
}

class ConversationStatusMapperImpl : ConversationStatusMapper {
    override fun toApiModel(mutedStatus: MutedConversationStatus, mutedStatusTimestamp: Long): MemberUpdateDTO {
        return MemberUpdateDTO(
            otrMutedStatus = MemberUpdateDTO.MutedStatus.fromOrdinal(mutedStatus.status),
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

    override fun fromApiToDaoModel(mutedStatus: MemberUpdateDTO.MutedStatus?): ConversationEntity.MutedStatus {
        return when (mutedStatus) {
            MemberUpdateDTO.MutedStatus.ALL_ALLOWED -> ConversationEntity.MutedStatus.ALL_ALLOWED
            MemberUpdateDTO.MutedStatus.ONLY_MENTIONS_ALLOWED -> ConversationEntity.MutedStatus.ONLY_MENTIONS_ALLOWED
            MemberUpdateDTO.MutedStatus.ALL_MUTED -> ConversationEntity.MutedStatus.ALL_MUTED
            else -> ConversationEntity.MutedStatus.ALL_ALLOWED
        }
    }

    override fun fromApiToDaoModel(mutedStatus: Int?): ConversationEntity.MutedStatus {
        return when(mutedStatus) {
            0 -> ConversationEntity.MutedStatus.ALL_ALLOWED
            1 -> ConversationEntity.MutedStatus.ONLY_MENTIONS_ALLOWED
            3 -> ConversationEntity.MutedStatus.ALL_MUTED
            else -> ConversationEntity.MutedStatus.ALL_ALLOWED
        }
    }

}
