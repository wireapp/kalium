package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.fold


/*
 Get all user contacts that are not in the conversation @P
 */
class GetAllContactsNotInConversationUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(conversationId: QualifiedID) =
        userRepository
            .getAllKnownUsersNotInConversation(conversationId)
            .fold({ Result.Failure(it) }, { Result.Success(it) })

}

sealed class Result {
    data class Success(val contactsNotInConversation: List<OtherUser>) : Result()
    data class Failure(val storageFailure: StorageFailure) : Result()
}
