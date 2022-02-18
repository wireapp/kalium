package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.conversation.ConversationApi
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.network.utils.isSuccessful
import com.wire.kalium.persistence.dao.ConversationDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface ConversationRepository {
    suspend fun fetchConversations(): Either<CoreFailure, Unit>
    suspend fun getConversationList(): Flow<List<Conversation>>
    suspend fun getConversationDetails(conversationId: ConversationId): Flow<Conversation>
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

    override suspend fun fetchConversations(): Either<CoreFailure, Unit> {
        val conversationsResponse = conversationApi.conversationsByBatch(null, 100)
        return if (!conversationsResponse.isSuccessful()) {
            Either.Left(CoreFailure.ServerMiscommunication)
        } else {
            conversationDAO.insertConversations(conversationsResponse.value.conversations.map(conversationMapper::fromApiModelToDaoModel))
            conversationsResponse.value.conversations.forEach { conversationsResponse ->
                conversationDAO.insertMembers(
                    memberMapper.fromApiModelToDaoModel(conversationsResponse.members),
                    idMapper.fromApiToDao(conversationsResponse.id)
                )
            }
            Either.Right(Unit)
        }
    }

    override suspend fun getConversationList(): Flow<List<Conversation>> {
        return conversationDAO.getAllConversations().map { it.map(conversationMapper::fromDaoModel) }
    }

    override suspend fun getConversationDetails(conversationId: ConversationId): Flow<Conversation> {
        return conversationDAO.getConversationByQualifiedID(idMapper.toDaoModel(conversationId))
            .filterNotNull()
            .map(conversationMapper::fromDaoModel)
    }

    /**
     * Fetches a list of all members' IDs or a given conversation including self user
     */
    private suspend fun getConversationMembers(conversationId: ConversationId): List<UserId> {
        return conversationDAO.getAllMembers(idMapper.toDaoModel(conversationId)).first().map { idMapper.fromDaoModel(it.user) }
    }

    /**
     * Fetches a list of all recipients for a given conversation including this very client
     */
    override suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>> {
        val allIds = getConversationMembers(conversationId).map(idMapper::toApiModel)
        val result = clientApi.listClientsOfUsers(allIds)

        return if (!result.isSuccessful()) {
            Either.Left(CoreFailure.ServerMiscommunication)
        } else {
            Either.Right(memberMapper.fromMapOfClientsResponseToRecipients(result.value))
        }
    }
}
