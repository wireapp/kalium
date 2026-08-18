/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.flow.first

/**
 * Checks whether the active one-to-one conversation with [otherUserId] is ready to be opened.
 * Proteus conversations are ready once present locally. MLS conversations are ready only when
 * their group exists in Core Crypto.
 */
public interface CheckOneToOneConversationIsReadyUseCase {
    public suspend operator fun invoke(otherUserId: UserId): Result

    public sealed interface Result {
        public data class Ready(val conversation: Conversation) : Result
        public data object NotReady : Result
        public data class Failure(val coreFailure: CoreFailure) : Result
    }
}

internal class CheckOneToOneConversationIsReadyUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val transactionProvider: CryptoTransactionProvider,
) : CheckOneToOneConversationIsReadyUseCase {

    override suspend fun invoke(otherUserId: UserId): CheckOneToOneConversationIsReadyUseCase.Result =
        when (val result = conversationRepository.observeOneToOneConversationWithOtherUser(otherUserId).first()) {
            is Either.Left -> if (result.value is StorageFailure.DataNotFound) {
                CheckOneToOneConversationIsReadyUseCase.Result.NotReady
            } else {
                CheckOneToOneConversationIsReadyUseCase.Result.Failure(result.value)
            }
            is Either.Right -> checkConversationReadiness(result.value)
        }

    private suspend fun checkConversationReadiness(
        conversation: Conversation
    ): CheckOneToOneConversationIsReadyUseCase.Result = when (val protocol = conversation.protocol) {
        Conversation.ProtocolInfo.Proteus -> CheckOneToOneConversationIsReadyUseCase.Result.Ready(conversation)
        is Conversation.ProtocolInfo.MLS ->
            transactionProvider.mlsTransaction("checkOneToOneConversationIsReady") { mlsContext ->
                mlsConversationRepository.hasEstablishedMLSGroup(mlsContext, protocol.groupId)
            }.fold(
                { failure -> CheckOneToOneConversationIsReadyUseCase.Result.Failure(failure) },
                { isEstablished ->
                    if (isEstablished) {
                        CheckOneToOneConversationIsReadyUseCase.Result.Ready(conversation)
                    } else {
                        CheckOneToOneConversationIsReadyUseCase.Result.NotReady
                    }
                }
            )
        is Conversation.ProtocolInfo.Mixed -> CheckOneToOneConversationIsReadyUseCase.Result.NotReady
    }
}
