package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.user.GetSelfUserUseCase
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class ObserveIsSelfConversationMemberUseCase(
    private val conversationRepository: ConversationRepository,
    private val observeSelf: GetSelfUserUseCase,
) {
    sealed class Result {
        data class Success(val isMember: Boolean) : Result()
        data class Failure(val coreFailure: CoreFailure) : Result()
    }

    suspend operator fun invoke(conversationId: ConversationId): Flow<Result> {
        return observeSelf().flatMapLatest {
            conversationRepository.observeIsUserMember(conversationId, it.id)
                .map { it.fold({ Result.Failure(it) }, { Result.Success(it) }) }
        }

    }
}
