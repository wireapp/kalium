package com.wire.kalium.logic.data.conversation

import com.wire.kalium.network.api.conversation.MemberUpdateRequest

interface ConversationStatusMapper {
    fun mutedStatusToApiModel(mutedStatus: MutedConversationStatus, mutedStatusTimestamp: Long): MemberUpdateRequest
}

class ConversationStatusMapperImpl : ConversationStatusMapper {
    override fun mutedStatusToApiModel(mutedStatus: MutedConversationStatus, mutedStatusTimestamp: Long): MemberUpdateRequest {
        return MemberUpdateRequest(
            otrMutedStatus = MemberUpdateRequest.MutedStatus.fromOrdinal(mutedStatus.status),
            otrMutedRef = mutedStatusTimestamp.toString()
        )
    }

}
