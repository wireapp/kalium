package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.ProtocolInfo
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.flatMap

interface RemoveMemberFromConversationUseCase {
    suspend operator fun invoke(conversationId: ConversationId, userIdList: List<UserId>)
}

class RemoveMemberFromConversationUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val clientRepository: ClientRepository
) : RemoveMemberFromConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId, userIdList: List<UserId>) {
        conversationRepository.detailsById(conversationId).flatMap { conversation ->
            when (conversation.protocol) {
                is ProtocolInfo.Proteus ->
                    conversationRepository.deleteMembers(userIdList, conversationId)

                is ProtocolInfo.MLS ->
                    clientRepository.currentClientId().flatMap { clientId ->
                        mlsConversationRepository.removeMembersFromMLSGroup(clientId, conversation.protocol.groupId, userIdList)
                    }
            }
        }
    }
}
