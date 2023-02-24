package com.wire.kalium.logic.feature.message

import kotlinx.coroutines.flow.Flow

class ObserveOngoingSelfDeletionMessagesUseCase(
    private val selfDeletingMessageManager: SelfDeletingMessageManager
) {
    operator fun invoke(): Flow<Map<String, Long>> = selfDeletingMessageManager.observePendingMessageDeletionState()

}
