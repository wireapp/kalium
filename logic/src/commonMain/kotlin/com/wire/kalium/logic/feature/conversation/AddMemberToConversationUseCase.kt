package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.network.api.conversation.AddParticipantResponse
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.ConversationEntity.ProtocolInfo.Proteus

interface AddMemberToConversationUseCase {
    suspend operator fun invoke(conversationId: ConversationId, clientId: ClientId, members: List<Member>)
}

class AddMemberToConversationUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val idMapper: IdMapper
) : AddMemberToConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId, clientId: ClientId, members: List<Member>) {

        val userIdsMembers = members.map {
            idMapper.toUserId(it)
        }
        conversationRepository.getConversationProtocolInfo(conversationId).flatMap { protocolInfo ->
            when (protocolInfo) {
                is Proteus -> conversationRepository.addMembers(members, conversationId).map {
                    when (it) {
                        is AddParticipantResponse.ConversationUnchanged -> {}
                        is AddParticipantResponse.UserAdded -> {
                            //persist the user in the db
                            conversationRepository.persistMembers(members, conversationId)
                        }
                    }
                }

                is ConversationEntity.ProtocolInfo.MLS -> {
                    mlsConversationRepository.addMemberToMLSGroup("", userIdsMembers)
                }
            }
        }
    }
}
