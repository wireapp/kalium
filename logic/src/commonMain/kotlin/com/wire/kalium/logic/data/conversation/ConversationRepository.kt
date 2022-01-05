package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.conversation.ConversationApi
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.network.utils.isSuccessful

class ConversationRepository(
    private val conversationApi: ConversationApi,
    private val clientApi: ClientApi,
    private val idMapper: IdMapper,
    private val conversationMapper: ConversationMapper,
    private val memberMapper: MemberMapper
) {

    suspend fun getConversationList(): List<Conversation> {
        val conversationsResponse = conversationApi.conversationsByBatch(null, 100)
        if (!conversationsResponse.isSuccessful()) {
            TODO("Error handling. Repository layer, a good place to use Either<Failure,Success> ?")
        }
        return conversationsResponse.value.conversations.map(conversationMapper::fromApiModel)
    }

    suspend fun getConversationDetails(conversationId: ConversationId): Either<CoreFailure, Conversation> {
        val conversationResponse = conversationApi.fetchConversationDetails(conversationId)

        if (!conversationResponse.isSuccessful()) {
            return Either.Left(CoreFailure.ServerMiscommunication)
        }
        return Either.Right(conversationMapper.fromApiModel(conversationResponse.value))
    }

    /**
     * Fetches a list of all members' IDs or a given conversation including self user
     */
    private suspend fun getConversationMembers(conversationId: ConversationId): Either<CoreFailure, List<UserId>> =
        getConversationDetails(conversationId).map { conversation ->
            val otherIds = conversation.members.otherMembers.map { it.id }
            val selfId = conversation.members.self.id
            (otherIds + selfId)
        }

    /**
     * Fetches a list of all recipients for a given conversation including this very client
     */
    suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>> = suspending {
        getConversationMembers(conversationId).flatMap { membersIds ->
            val allIds = membersIds.map { id -> idMapper.toApiModel(id) }

            val result = clientApi.listClientsOfUsers(allIds)
            if (!result.isSuccessful()) {
                Either.Left(CoreFailure.ServerMiscommunication)
            } else {
                Either.Right(memberMapper.fromMapOfClientsResponseToRecipients(result.value))
            }
        }
    }
}
