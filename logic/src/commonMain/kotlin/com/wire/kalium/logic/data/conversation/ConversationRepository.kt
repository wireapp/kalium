package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.conversation.ConversationApi
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.Member
import com.wire.kalium.persistence.dao.QualifiedID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface ConversationRepository {
    suspend fun fetchConversations(): Either<CoreFailure, Unit>
    suspend fun getConversationList(): Flow<List<Conversation>>
    suspend fun getConversationDetails(conversationId: ConversationId): Flow<Conversation>
    suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>>
    suspend fun persistMember(member: Member, conversationID: QualifiedID): Either<CoreFailure, Unit>
    suspend fun persistMembers(members: List<Member>, conversationID: QualifiedID): Either<CoreFailure, Unit>
    suspend fun deleteMember(conversationID: QualifiedID, userID: QualifiedID): Either<CoreFailure, Unit>
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

    override suspend fun persistMember(member: Member, conversationID: QualifiedID): Either<CoreFailure, Unit> {
        conversationDAO.insertMember(member, conversationID)
        //TODO: Handle failures
        return Either.Right(Unit)
    }

    override suspend fun persistMembers(members: List<Member>, conversationID: QualifiedID): Either<CoreFailure, Unit> {
        conversationDAO.insertMembers(members, conversationID)
        //TODO: Handle failures
        return Either.Right(Unit)
    }

    override suspend fun deleteMember(conversationID: QualifiedID, userID: QualifiedID): Either<CoreFailure, Unit> {
        conversationDAO.deleteMemberByQualifiedID(conversationID, userID)
        //TODO: Handle failures
        return Either.Right(Unit)
    }

    /**
     * Fetches a list of all recipients for a given conversation including this very client
     */
    override suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>> {
        val allIds = getConversationMembers(conversationId).map(idMapper::toApiModel)
        return wrapApiRequest { clientApi.listClientsOfUsers(allIds) }.map { memberMapper.fromMapOfClientsResponseToRecipients(it) }
    }
}
