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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case that get Conversation.ProtocolInfo for a specific conversation.
 * @see Conversation.ProtocolInfo
 */
interface GetConversationProtocolInfoUseCase {
    sealed class Result {
        data class Success(val protocolInfo: Conversation.ProtocolInfo) : Result()
        data class Failure(val storageFailure: StorageFailure) : Result()
    }

    /**
     * @param conversationId the id of the conversation to observe
     * @return a [Result] with the [Conversation.ProtocolInfo] of the conversation
     */
    suspend operator fun invoke(conversationId: ConversationId): Result
}

@Suppress("FunctionNaming")
internal fun GetConversationProtocolInfoUseCase(
    conversationRepository: ConversationRepository,
    dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) = object : GetConversationProtocolInfoUseCase {
    override suspend operator fun invoke(conversationId: ConversationId): GetConversationProtocolInfoUseCase.Result =
        withContext(dispatcher.io) {
            conversationRepository.getConversationProtocolInfo(conversationId)
                .fold({ GetConversationProtocolInfoUseCase.Result.Failure(it) }, { GetConversationProtocolInfoUseCase.Result.Success(it) })
        }
}
