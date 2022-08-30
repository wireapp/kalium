package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.fold

class GetAllContactsNotInConversationUseCase(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(conversationId: QualifiedID) =
        userRepository
            .getAllKnownUsersNotInConversation(conversationId)
            .fold(ClassifiedTypeResult::Failure, ClassifiedTypeResult::Success)

}

sealed class Result {
    data class Success(val contactsNotInConversation: List<OtherUser>) : ClassifiedTypeResult()
    data class Failure(val storageFailure: StorageFailure) : ClassifiedTypeResult()
}
