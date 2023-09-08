/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.flatMapRight
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * This use case will observe and return the conversation details for a specific conversation.
 * Also it will observe conversation verification status and update it when needed.
 * @see ConversationDetails
 */
interface ObserveConversationDetailsUseCase {
    suspend operator fun invoke(conversationId: ConversationId): Flow<ObserveConversationDetailsResult>
}

internal class ObserveConversationDetailsUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val verificationStatusHandler: ConversationVerificationStatusHandler,
) : ObserveConversationDetailsUseCase {

    /**
     * @param conversationId the id of the conversation to observe
     * @return a flow of [Result] with the [ConversationDetails] of the conversation
     */
    override suspend operator fun invoke(conversationId: ConversationId): Flow<ObserveConversationDetailsResult> {
        return conversationRepository.observeConversationDetailsById(conversationId)
            .flatMapRight { protocolInfo ->
                // just observe a Flow from verificationStatusHandler, but ignore it,
                // so handler fetch and save verification status.
                verificationStatusHandler(conversationId)
                    .map { protocolInfo }
                    .onStart { emit(protocolInfo) }
            }
            .map { it.fold({ l -> ObserveConversationDetailsResult.Failure(l) }, { r -> ObserveConversationDetailsResult.Success(r) }) }
    }
}

sealed class ObserveConversationDetailsResult {
    data class Success(val conversationDetails: ConversationDetails) : ObserveConversationDetailsResult()
    data class Failure(val storageFailure: StorageFailure) : ObserveConversationDetailsResult()
}
