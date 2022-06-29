package com.wire.kalium.logic.feature.publicuser.search

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map

class ConversationMembersExcludedSearchUseCase(
    private val searchUsersUseCase: SearchUsersUseCase,
    private val conversationRepository: ConversationRepository
) {

    suspend operator fun invoke(
        conversationId: ConversationId,
        searchQuery: String
    ) = when (val result = searchUsersUseCase(searchQuery)) {
        is Result.Success -> removeConversationMembersFromSearchResult(
            userSearchResult = result.userSearchResult,
            conversationId = conversationId
        )
        else -> result
    }

    private suspend fun removeConversationMembersFromSearchResult(
        userSearchResult: UserSearchResult,
        conversationId: ConversationId
    ) = conversationRepository.getConversationMembers(conversationId).map { conversationMembers ->
        userSearchResult.copy(
            result = userSearchResult.result.filter { conversationMember -> !conversationMembers.contains(conversationMember.id) }
        )
    }.fold({ Result.Failure.Generic(it) }, { Result.Success(it) })

}
