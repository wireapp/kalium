package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Operation that fetches all known users which are not a part of a given conversation [conversationId]
 *
 * @param conversationId
 * @return Result with list of known users not being a part of a conversation
 */
class GetAllContactsNotInConversationUseCase internal constructor(
    private val userRepository: UserRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {
    suspend operator fun invoke(conversationId: ConversationId): Flow<Result> = withContext(dispatcher.default) {
        userRepository
            .observeAllKnownUsersNotInConversation(conversationId)
            .map { it.fold(Result::Failure, Result::Success) }
    }
}

sealed class Result {
    data class Success(val contactsNotInConversation: List<OtherUser>) : Result()
    data class Failure(val storageFailure: StorageFailure) : Result()
}
