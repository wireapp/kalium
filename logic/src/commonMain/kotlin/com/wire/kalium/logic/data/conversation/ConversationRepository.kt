package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.conversation.ConversationApi
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.MemberEntity
import com.wire.kalium.persistence.dao.QualifiedID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface ConversationRepository {
    suspend fun fetchConversations(): Either<CoreFailure, Unit>
    suspend fun getConversationList(): Either<StorageFailure, Flow<List<Conversation>>>
    suspend fun getConversationDetails(conversationId: ConversationId): Either<StorageFailure, Flow<Conversation>>
    suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>>
    suspend fun persistMember(memberEntity: MemberEntity, conversationID: QualifiedID): Either<CoreFailure, Unit>
    suspend fun persistMembers(memberEntity: List<MemberEntity>, conversationID: QualifiedID): Either<CoreFailure, Unit>
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

    // FIXME: fetchConversations() returns only the first page
    // TODO: rewrite to use wrapStorageRequest
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


    override suspend fun getConversationList(): Either<StorageFailure, Flow<List<Conversation>>> = wrapStorageRequest {
        conversationDAO.getAllConversations().map { it.map(conversationMapper::fromEntity) }
    }


    override suspend fun getConversationDetails(conversationId: ConversationId): Either<StorageFailure, Flow<Conversation>> =
        wrapStorageRequest {
            conversationDAO.getConversationByQualifiedID(idMapper.toDaoModel(conversationId))
                .filterNotNull()
                .map(conversationMapper::fromEntity)
        }

    /**
     * Fetches a list of all members' IDs or a given conversation including self user
     */
    private suspend fun getConversationMembers(conversationId: ConversationId): Either<StorageFailure, List<UserId>> = wrapStorageRequest {
        conversationDAO.getAllMembers(idMapper.toDaoModel(conversationId)).first().map { idMapper.fromDaoModel(it.user) }
    }

    override suspend fun persistMember(memberEntity: MemberEntity, conversationID: QualifiedID): Either<CoreFailure, Unit> =
        wrapStorageRequest {
            conversationDAO.insertMember(memberEntity, conversationID)
        }


    override suspend fun persistMembers(memberEntity: List<MemberEntity>, conversationID: QualifiedID): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            conversationDAO.insertMembers(memberEntity, conversationID)
        }

    override suspend fun deleteMember(conversationID: QualifiedID, userID: QualifiedID): Either<CoreFailure, Unit> = wrapStorageRequest {
        conversationDAO.deleteMemberByQualifiedID(conversationID, userID)
    }

    /**
     * Fetches a list of all recipients for a given conversation including this very client
     */
    override suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>> = suspending {
        getConversationMembers(conversationId)
            .map { it.map(idMapper::toApiModel) }
            .flatMap {
                wrapApiRequest { clientApi.listClientsOfUsers(it) }.map { memberMapper.fromMapOfClientsResponseToRecipients(it) }
            }
    }
}
