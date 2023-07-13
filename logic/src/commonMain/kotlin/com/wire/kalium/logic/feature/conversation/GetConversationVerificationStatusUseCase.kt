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
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationVerificationStatus
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.map
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Operation that fetches [ConversationVerificationStatus] of the specific conversation
 *
 * @param conversationId
 * @return [ConversationVerificationStatusResult.Failure] when error occurred.
 * [ConversationVerificationStatusResult.Success] with [ConversationVerificationStatus] and [ConversationProtocol] of the conversation
 * in other cases.
 */
interface GetConversationVerificationStatusUseCase {
    suspend operator fun invoke(conversationId: ConversationId): ConversationVerificationStatusResult
}

class GetConversationVerificationStatusUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : GetConversationVerificationStatusUseCase {
    private val dispatcher = kaliumDispatcher.io

    override suspend fun invoke(conversationId: ConversationId): ConversationVerificationStatusResult = withContext(dispatcher) {
        conversationRepository.baseInfoById(conversationId)
            .flatMap { conversation ->
                if (conversation.protocol is Conversation.ProtocolInfo.MLS) {
                    getConversationMLSVerificationStatus(conversation.protocol)
                } else {
                    getConversationProteusVerificationStatus(conversation.id)
                }
            }.getOrElse { ConversationVerificationStatusResult.Failure(it) }
    }

    private suspend fun getConversationMLSVerificationStatus(protocol: Conversation.ProtocolInfo.MLS) =
        mlsConversationRepository.getConversationVerificationStatus(protocol.groupId)
            .map { ConversationVerificationStatusResult.Success(ConversationProtocol.MLS, it) }

    private suspend fun getConversationProteusVerificationStatus(
        conversationId: ConversationId
    ): Either<CoreFailure, ConversationVerificationStatusResult> {
        // TODO  needs to be handled by for Proteus conversation that is not implemented yet
        return Either.Right(
            ConversationVerificationStatusResult.Success(
                ConversationProtocol.PROTEUS,
                ConversationVerificationStatus.NOT_VERIFIED
            )
        )
    }
}

sealed class ConversationVerificationStatusResult {
    data class Success(
        val protocol: ConversationProtocol,
        val status: ConversationVerificationStatus
    ) : ConversationVerificationStatusResult()

    data class Failure(val storageFailure: CoreFailure) : ConversationVerificationStatusResult()
}

enum class ConversationProtocol {
    PROTEUS, MLS
}
