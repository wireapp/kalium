package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.flow.Flow

class ObserveOngoingSelfDeletionMessagesUseCase(
    private val selfDeletingMessageManager: SelfDeletingMessageManager
) {
    operator fun invoke(): Flow<Map<Pair<ConversationId, String>, Long>> = selfDeletingMessageManager.observePendingMessageDeletionState()

}
