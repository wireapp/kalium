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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.combine
import com.wire.kalium.logic.functional.flatMapRight
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * Operation that observes [Conversation.ProtocolInfo] of the specific conversation
 *
 * @param conversationId
 * @return [Flow] of: [ConversationVerificationStatusResult.Failure] when error occurred.
 * [ConversationVerificationStatusResult.Success] with [Conversation.ProtocolInfo]
 * in other cases.
 */
internal interface ObserveConversationProtocolInfoUseCase {
    suspend operator fun invoke(conversationId: ConversationId): Flow<ConversationVerificationStatusResult>
}

internal class ObserveConversationProtocolInfoUseCaseImpl internal constructor(
    private val conversationRepository: ConversationRepository,
    private val verificationStatusHandler: ConversationVerificationStatusHandler,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : ObserveConversationProtocolInfoUseCase {
    private val dispatcher = kaliumDispatcher.io

    override suspend fun invoke(conversationId: ConversationId): Flow<ConversationVerificationStatusResult> = withContext(dispatcher) {
        conversationRepository.observeConversationProtocolInfo(conversationId)
            .flatMapRight { protocolInfo ->
                // just observe a Flow from verificationStatusHandler, but ignore it,
                // so handler fetch and save verification status.
                verificationStatusHandler(conversationId)
                    .map { protocolInfo }
                    .onStart { emit(protocolInfo) }
            }
            .distinctUntilChanged()
            .mapLatest { either ->
                either.fold({ ConversationVerificationStatusResult.Failure(it) }) {
                    ConversationVerificationStatusResult.Success(it)
                }
            }
    }
}

sealed class ConversationVerificationStatusResult {
    data class Success(val protocolInfo: Conversation.ProtocolInfo) : ConversationVerificationStatusResult()
    data class Failure(val storageFailure: CoreFailure) : ConversationVerificationStatusResult()
}
