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
import io.mockative.Mockable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * This use case is responsible for updating conversation clients in a call
 * Usually called when a member is removed from conversation
 */
@Mockable
interface UpdateConversationClientsForCurrentCallUseCase {
    suspend operator fun invoke(conversationId: ConversationId)
}

internal class UpdateConversationClientsForCurrentCallUseCaseImpl(
    private val callRepository: CallRepository,
    private val conversationClientsInCallUpdater: ConversationClientsInCallUpdater
) : UpdateConversationClientsForCurrentCallUseCase {
    override suspend fun invoke(conversationId: ConversationId) {
        callRepository.establishedCallsFlow().map { calls -> calls.map { it.conversationId } }.first().find {
            it == conversationId
        }?.let {
            conversationClientsInCallUpdater(conversationId)
        }
    }
}
