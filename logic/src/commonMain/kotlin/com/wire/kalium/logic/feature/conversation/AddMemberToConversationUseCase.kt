package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.conversation.ProtocolInfo
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.flatMap

interface AddMemberToConversationUseCase {
    suspend operator fun invoke(conversationId: ConversationId, members: List<Member>)
}

class AddMemberToConversationUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository
) : AddMemberToConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId, members: List<Member>) {

        val membersUserIds = members.map { it.id }
        conversationRepository.detailsById(conversationId).flatMap { conversation ->
            when (conversation.protocol) {
                is ProtocolInfo.Proteus -> conversationRepository.addMembers(members, conversationId)
                is ProtocolInfo.MLS ->
                    mlsConversationRepository.addMemberToMLSGroup(conversation.protocol.groupId, membersUserIds)
            }
        }
    }
}
