package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.publicuser.Result
import com.wire.kalium.logic.feature.publicuser.SearchKnownUsersUseCase
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map

class SearchKnownUsersNotPartOfConversationUseCase(
    private val searchKnownUsersUseCase: SearchKnownUsersUseCase,
    private val conversationRepository: ConversationRepository
) {

    suspend operator fun invoke(conversationId: ConversationId, searchQuery: String): Result {
        return when (val result = searchKnownUsersUseCase(searchQuery)) {
            is Result.Success -> removeConversationMembersFromSearchResult(result.userSearchResult, conversationId)
            else -> result
        }
    }

    private suspend fun removeConversationMembersFromSearchResult(
        userSearchResult: UserSearchResult,
        conversationId: ConversationId
    ): Result {
        return conversationRepository.getConversationMembers(conversationId).map { conversationMembers ->
            userSearchResult.copy(
                result = userSearchResult.result.filter { conversationMember -> !conversationMembers.contains(conversationMember.id) }
            )
        }.fold({ Result.Failure.Generic(it) }, { Result.Success(it) })
    }

}
