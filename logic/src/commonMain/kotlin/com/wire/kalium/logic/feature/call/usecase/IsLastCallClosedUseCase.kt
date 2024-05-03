/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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
