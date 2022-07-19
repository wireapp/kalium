package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface IsLastCallClosedUseCase {
    suspend operator fun invoke(conversationId: ConversationId, startedTime: Long): Flow<Boolean>
}

internal class IsLastCallClosedUseCaseImpl(
    private val callRepository: CallRepository
) : IsLastCallClosedUseCase {

    override suspend fun invoke(conversationId: ConversationId, startedTime: Long): Flow<Boolean> =
        callRepository
            .getLastClosedCallCreatedByConversationId(conversationId = conversationId)
            .map {
                val createdAt = it // format to time/long
                // (createdAt >= startedTime) ^map

                false
            }
}
