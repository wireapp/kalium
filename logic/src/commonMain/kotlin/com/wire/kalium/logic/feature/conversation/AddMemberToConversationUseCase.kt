package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.conversation.ProtocolInfo
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.network.api.conversation.AddParticipantResponse

interface AddMemberToConversationUseCase {
    suspend operator fun invoke(conversationId: ConversationId, members: List<Member>)
}

class AddMemberToConversationUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val idMapper: IdMapper
) : AddMemberToConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId, members: List<Member>) {

        val userIdsMembers = members.map {
            idMapper.toUserId(it)
        }
        conversationRepository.getConversationDetailsById(conversationId).flatMap { conversation ->
            when (conversation.protocolInfo) {
                is ProtocolInfo.Proteus -> conversationRepository.addMembers(members, conversationId).map {
                    when (it) {
                        is AddParticipantResponse.ConversationUnchanged -> Unit
                        is AddParticipantResponse.UserAdded -> {
                            conversationRepository.persistMembers(members, conversationId)
                        }
                    }
                }

                is ProtocolInfo.MLS -> {
                    mlsConversationRepository.addMemberToMLSGroup(conversation.protocolInfo.groupId, userIdsMembers)
                }
            }
        }
    }
}
