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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.Mockable
import kotlinx.coroutines.withContext

/**
 * Use case to get the current MLS epoch for a conversation.
 * The epoch is retrieved from the MLS context based on the conversation's group ID.
 *
 * @see Conversation.ProtocolInfo.MLSCapable
 */
@Mockable
interface GetConversationEpochUseCase {
    sealed class Result {
        data class Success(val epoch: ULong) : Result()
        data class Failure(val coreFailure: CoreFailure) : Result()
    }

    /**
     * @param groupId the MLS group ID to get the epoch for
     * @return a [Result] with the current epoch of the group
     */
    suspend operator fun invoke(groupId: GroupID): Result
}

@Suppress("FunctionNaming")
class GetConversationEpochUseCaseImpl internal constructor(
    private val cryptoTransactionProvider: CryptoTransactionProvider,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : GetConversationEpochUseCase {
    override suspend operator fun invoke(groupId: GroupID): GetConversationEpochUseCase.Result =
        withContext(dispatcher.io) {
            val result = cryptoTransactionProvider.mlsTransaction { mlsContext ->
                wrapMLSRequest {
                    mlsContext.conversationEpoch(groupId.toCrypto())
                }
            }

            result.fold(
                { failure -> GetConversationEpochUseCase.Result.Failure(failure) },
                { epoch -> GetConversationEpochUseCase.Result.Success(epoch) }
            )
        }
}
