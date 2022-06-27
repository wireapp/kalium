package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map

class GetAllContactsNotInTheConversationUseCase(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository
) {
    suspend fun invoke(conversationId: QualifiedID) =
        conversationRepository.getConversationMembers(conversationId).flatMap { conversationMembers ->
            userRepository.getAllContacts()
                .map { allContacts -> removeConversationMembersFromAllContacts(allContacts, conversationMembers) }
        }.fold({ Result.Failure(it) }, { Result.Success(it) })

    private fun removeConversationMembersFromAllContacts(otherUsers: List<OtherUser>, conversationMembers: List<UserId>) =
        otherUsers.filter { conversationMember -> conversationMembers.contains(conversationMember.id) }
}

sealed class Result {
    data class Success(val contactNotInTheConversation: List<OtherUser>) : Result()
    data class Failure(val storageFailure: StorageFailure) : Result()
}


