package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * This use case will observe and return the list of conversation details for the current user.
 * @see ConversationDetails
 */
fun interface ObserveConversationListDetailsUseCase {
    suspend operator fun invoke(): Flow<List<ConversationDetails>>
}

internal class ObserveConversationListDetailsUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : ObserveConversationListDetailsUseCase {

    override suspend operator fun invoke(): Flow<List<ConversationDetails>> = withContext(dispatcher.default) {
        conversationRepository.observeConversationListDetails()
    }
}
