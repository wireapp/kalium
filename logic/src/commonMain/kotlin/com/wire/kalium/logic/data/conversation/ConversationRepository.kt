package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.conversation.ConversationApi
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.persistence.dao.ConversationDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface ConversationRepository {
    suspend fun fetchConversations(): Either<CoreFailure, Unit>
    suspend fun getConversationList(): Flow<List<Conversation>>
    suspend fun getConversationDetails(conversationId: ConversationId): Either<CoreFailure, Conversation>
    suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>>
}

class ConversationDataSource(
    private val conversationDAO: ConversationDAO,
    private val conversationApi: ConversationApi,
    private val clientApi: ClientApi,
    private val idMapper: IdMapper,
    private val conversationMapper: ConversationMapper,
    private val memberMapper: MemberMapper
) : ConversationRepository {

    // TODO: this need a review after the new wrapApiRequest
    // FIXME: fetchConversations() returns only the first page
    override suspend fun fetchConversations(): Either<CoreFailure, Unit> = suspending {
        wrapApiRequest { conversationApi.conversationsByBatch(null, 100) }.map { conversationPagingResponse ->
            conversationDAO.insertConversations(conversationPagingResponse.conversations.map(conversationMapper::fromApiModelToDaoModel))
            conversationPagingResponse.conversations.forEach { conversationsResponse ->
                conversationDAO.insertMembers(
                    memberMapper.fromApiModelToDaoModel(conversationsResponse.members),
                    idMapper.fromApiToDao(conversationsResponse.id)
                )
            }
        }
    }


    override suspend fun getConversationList(): Flow<List<Conversation>> {
        return conversationDAO.getAllConversations().map { it.map(conversationMapper::fromDaoModel) }
    }

    // TODO: handle 404 ResourceNotFound
    override suspend fun getConversationDetails(conversationId: ConversationId): Either<NetworkFailure, Conversation> =
        wrapApiRequest {
            conversationApi.fetchConversationDetails(conversationId)
        }.map { conversationMapper.fromApiModel(it) }

    /**
     * Fetches a list of all members' IDs or a given conversation including self user
     */
    private suspend fun getConversationMembers(conversationId: ConversationId): List<UserId> {
        return conversationDAO.getAllMembers(idMapper.fromApiToDao(conversationId)).first().map { idMapper.fromDaoModel(it.user) }
    }

    /**
     * Fetches a list of all recipients for a given conversation including this very client
     */
    override suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>> {
        val allIds = getConversationMembers(conversationId).map(idMapper::toApiModel)
        return wrapApiRequest { clientApi.listClientsOfUsers(allIds) }.map { memberMapper.fromMapOfClientsResponseToRecipients(it) }
    }
}
