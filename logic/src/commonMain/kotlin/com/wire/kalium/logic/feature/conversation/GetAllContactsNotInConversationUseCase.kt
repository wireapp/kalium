package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetAllContactsNotInConversationUseCase internal constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(conversationId: QualifiedID): Flow<Result> =
        userRepository
            .observeAllKnownUsersNotInConversation(conversationId)
            .map { it.fold(Result::Failure, Result::Success) }

}

sealed class Result {
    data class Success(val contactsNotInConversation: List<OtherUser>) : Result()
    data class Failure(val storageFailure: StorageFailure) : Result()
}
