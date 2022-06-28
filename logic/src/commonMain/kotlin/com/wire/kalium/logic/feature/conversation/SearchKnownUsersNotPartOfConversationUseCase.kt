package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.publicuser.Result
import com.wire.kalium.logic.feature.publicuser.SearchKnownUsersUseCase

class SearchKnownUsersNotPartOfConversationUseCase(
    private val searchKnownUsersUseCase: SearchKnownUsersUseCase,
    private val userRepository: UserRepository
) {

    suspend operator fun invoke(conversationId: ConversationId, searchQuery: String): Result {
        return when (val result = searchKnownUsersUseCase(searchQuery)) {
            is Result.Success -> {
                Result.Success(
                    result.copy(userSearchResult = removeConversationMembersFromAllContacts())
                )
            }
            else -> result
        }
    }

    private fun removeConversationMembersFromAllContacts(allContacts: List<OtherUser>, conversationMembers: List<UserId>) =
        allContacts.filter { conversationMember -> !conversationMembers.contains(conversationMember.id) }
}
