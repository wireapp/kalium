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
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapCryptoRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi

/**
 * Leave a sub-conversation you've previously joined
 */
interface LeaveSubconversationUseCase {
    suspend operator fun invoke(conversationId: ConversationId, subconversationId: SubconversationId): Either<CoreFailure, Unit>
}

class LeaveSubconversationUseCaseImpl(
    val conversationApi: ConversationApi,
    val mlsClientProvider: MLSClientProvider,
    val subconversationRepository: SubconversationRepository
) : LeaveSubconversationUseCase {
    override suspend fun invoke(conversationId: ConversationId, subconversationId: SubconversationId): Either<CoreFailure, Unit> =
        wrapApiRequest {
            conversationApi.leaveSubconversation(conversationId.toApi(), subconversationId.toApi())
        }.flatMap {
            mlsClientProvider.getMLSClient().flatMap { mlsClient ->
                subconversationRepository.getSubconversationInfo(conversationId, subconversationId)?.let {
                    wrapCryptoRequest {
                        mlsClient.wipeConversation(it.toCrypto())
                    }
                } ?: Either.Right(Unit)

            }
        }
}
