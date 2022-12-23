package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * This use case checks whether the last call in a conversation is closed or not.
 * fixme: rename to ObservesLastCallClosedUseCase
 */
interface IsLastCallClosedUseCase {
    /**
     * @param conversationId the id of the conversation.
     * @return a [Flow] of a boolean that indicates whether the last call in the conversation is closed or not.
     */
    suspend operator fun invoke(conversationId: ConversationId, startedTime: Long): Flow<Boolean>
}

internal class IsLastCallClosedUseCaseImpl(
    private val callRepository: CallRepository
) : IsLastCallClosedUseCase {

    override suspend fun invoke(conversationId: ConversationId, startedTime: Long): Flow<Boolean> =
        callRepository
            .getLastClosedCallCreatedByConversationId(conversationId = conversationId)
            .map {
                it?.let { createdAt ->
                    createdAt.toLong() >= startedTime
                } ?: false
            }
}
