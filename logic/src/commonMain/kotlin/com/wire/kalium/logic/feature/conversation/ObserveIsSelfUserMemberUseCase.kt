package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface ObserveIsSelfUserMemberUseCase {
    /**
     * Use case that check if self user is member of given conversation
     * @param conversationId the id of the conversation where user checks his membership
     * @return an [IsSelfUserMemberResult] containing Success or Failure cases
     */
    suspend operator fun invoke(
        conversationId: ConversationId
    ): Flow<IsSelfUserMemberResult>
}

internal class ObserveIsSelfUserMemberUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val selfUserId: UserId,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : ObserveIsSelfUserMemberUseCase {

    override suspend operator fun invoke(conversationId: ConversationId): Flow<IsSelfUserMemberResult> = withContext(dispatcher.default) {
        conversationRepository.observeIsUserMember(conversationId, selfUserId)
            .map { it.fold({ IsSelfUserMemberResult.Failure(it) }, { IsSelfUserMemberResult.Success(it) }) }
    }
}

sealed class IsSelfUserMemberResult {
    data class Success(val isMember: Boolean) : IsSelfUserMemberResult()
    data class Failure(val coreFailure: CoreFailure) : IsSelfUserMemberResult()
}
