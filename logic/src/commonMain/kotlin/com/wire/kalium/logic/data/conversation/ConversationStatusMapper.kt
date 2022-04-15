package com.wire.kalium.logic.data.conversation

import com.wire.kalium.network.api.conversation.MemberUpdateRequest
import com.wire.kalium.persistence.dao.ConversationEntity
import kotlinx.datetime.Instant

interface ConversationStatusMapper {
    fun toApiModel(mutedStatus: MutedConversationStatus, mutedStatusTimestamp: Long): MemberUpdateRequest
    fun toDaoModel(mutedStatus: MutedConversationStatus): ConversationEntity.MutedStatus
}

class ConversationStatusMapperImpl : ConversationStatusMapper {
    override fun toApiModel(mutedStatus: MutedConversationStatus, mutedStatusTimestamp: Long): MemberUpdateRequest {
        return MemberUpdateRequest(
            otrMutedStatus = MemberUpdateRequest.MutedStatus.fromOrdinal(mutedStatus.status),
            otrMutedRef = Instant.fromEpochMilliseconds(mutedStatusTimestamp).toString()
        )
    }

    override fun toDaoModel(mutedStatus: MutedConversationStatus): ConversationEntity.MutedStatus {
        return when (mutedStatus) {
            MutedConversationStatus.AllAllowed -> ConversationEntity.MutedStatus.ALL_ALLOWED
            MutedConversationStatus.OnlyMentionsAllowed -> ConversationEntity.MutedStatus.ONLY_MENTIONS_ALLOWED
            MutedConversationStatus.AllMuted -> ConversationEntity.MutedStatus.ALL_MUTED
            else -> ConversationEntity.MutedStatus.MENTIONS_MUTED // legacy, not used
        }
    }

}
