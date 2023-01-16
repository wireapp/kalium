package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case will sync against the backend the conversations of the current user.
 */
class SyncConversationsUseCase(
    private val conversationRepository: ConversationRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {

    suspend operator fun invoke(): Either<CoreFailure, Unit> = withContext(dispatcher.default) {
        conversationRepository.fetchConversations()
    }

}
