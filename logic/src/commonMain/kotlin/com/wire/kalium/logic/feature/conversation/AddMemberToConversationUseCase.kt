package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.conversation.ProtocolInfo
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.flatMap

interface AddMemberToConversationUseCase {
    suspend operator fun invoke(conversationId: ConversationId, userIdList: List<UserId>)
}

class AddMemberToConversationUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository
) : AddMemberToConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId, userIdList: List<UserId>) {
        // TODO: do we need to filter out self user ?
        conversationRepository.detailsById(conversationId).flatMap { conversation ->
            when (conversation.protocol) {
                is ProtocolInfo.Proteus -> conversationRepository.addMembers(userIdList, conversationId)
                is ProtocolInfo.MLS ->
                    mlsConversationRepository.addMemberToMLSGroup(conversation.protocol.groupId, userIdList)
            }
        }
    }
}
