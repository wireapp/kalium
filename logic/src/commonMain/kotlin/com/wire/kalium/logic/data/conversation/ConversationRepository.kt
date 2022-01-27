package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.failure.ResourceNotFound
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.QualifiedID
import com.wire.kalium.network.api.conversation.ConversationApi
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.network.api.user.details.ListUserRequest
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.UserDetailsResponse
import com.wire.kalium.network.api.user.details.qualifiedIds
import com.wire.kalium.network.utils.isSuccessful

interface ConversationRepository {
    suspend fun getConversationList(): Either<CoreFailure, List<Conversation>>
    suspend fun getConversationDetails(conversationId: ConversationId): Either<CoreFailure, Conversation>
    suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>>
}

class ConversationDataSource(
    private val conversationApi: ConversationApi,
    private val clientApi: ClientApi,
    private val idMapper: IdMapper,
    private val conversationMapper: ConversationMapper,
    private val memberMapper: MemberMapper,
    private val userDetailsApi: UserDetailsApi
) : ConversationRepository {

    override suspend fun getConversationList(): Either<CoreFailure, List<Conversation>> {
        val conversationsResponse = conversationApi.conversationsByBatch(null, 100)
        return if (!conversationsResponse.isSuccessful()) {
            Either.Left(CoreFailure.ServerMiscommunication)
        } else {
            val conversations = conversationsResponse.value.conversations
            getUserDetailsForOneOnOneConversations(conversations).map { users ->
                fillConversationNames(conversations, users)
            }.map {
                it.map((conversationMapper::fromApiModel))
            }
        }
    }

    private val ConversationResponse.shouldHaveNameReplaced: Boolean
        get() = type !in setOf(ConversationResponse.Type.GROUP, ConversationResponse.Type.UNKNOWN) && members.otherMembers.size <= 1

    private fun fillConversationNames(
        conversations: List<ConversationResponse>,
        users: List<UserDetailsResponse>
    ) = conversations.map { conversation ->
        val firstContactId = conversation.members.otherMembers.firstOrNull()?.userId
        val contactDetails = users.firstOrNull { userDetail -> userDetail.id == firstContactId }
        if (!conversation.shouldHaveNameReplaced || contactDetails == null) {
            conversation
        } else {
            // Update the name with the details found for the contact
            conversation.copy(name = contactDetails.name)
        }
    }

    private suspend fun getUserDetailsForOneOnOneConversations(conversations: List<ConversationResponse>)
            : Either<CoreFailure, List<UserDetailsResponse>> {
        val neededUserIds = arrayListOf<QualifiedID>()
        conversations.forEach { conversation ->
            if (!conversation.shouldHaveNameReplaced) {
                return@forEach
            }
            conversation.members.otherMembers.firstOrNull()?.userId?.let { neededUserIds += it }
        }
        val response = userDetailsApi.getMultipleUsers(ListUserRequest.qualifiedIds(neededUserIds.distinct()))
        return if (!response.isSuccessful()) {
            Either.Left(CoreFailure.ServerMiscommunication)
        } else {
            Either.Right(response.value)
        }
    }

    override suspend fun getConversationDetails(conversationId: ConversationId): Either<CoreFailure, Conversation> {
        val conversationResponse = conversationApi.fetchConversationDetails(conversationId)

        if (!conversationResponse.isSuccessful()) {
            if (conversationResponse.kException.errorCode == 404) {
                return Either.Left(ResourceNotFound)
            }
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
    override suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>> = suspending {
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
