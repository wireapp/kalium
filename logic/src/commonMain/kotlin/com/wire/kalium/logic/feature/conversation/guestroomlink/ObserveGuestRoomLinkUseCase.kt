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

package com.wire.kalium.logic.feature.conversation.guestroomlink

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationGuestLink
import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public interface ObserveGuestRoomLinkUseCase {
    /**
     * Observe guest room link of a conversation.
     * @param conversationId The conversation id to observe the guest room link.
     * @return A flow emitting [ObserveGuestRoomLinkResult] with the guest room link if exists or failure.
     */
    public suspend operator fun invoke(conversationId: ConversationId): Flow<ObserveGuestRoomLinkResult>
}

public sealed class ObserveGuestRoomLinkResult {
    public data class Success(val link: ConversationGuestLink?) : ObserveGuestRoomLinkResult()
    public data class Failure(val failure: CoreFailure) : ObserveGuestRoomLinkResult()
}

internal class ObserveGuestRoomLinkUseCaseImpl internal constructor(
    private val conversationGroupRepository: ConversationGroupRepository
) : ObserveGuestRoomLinkUseCase {
    override suspend fun invoke(conversationId: ConversationId): Flow<ObserveGuestRoomLinkResult> {
        return conversationGroupRepository.observeGuestRoomLink(conversationId).map {
            if (it.isRight()) {
                ObserveGuestRoomLinkResult.Success(it.value)
            } else {
                ObserveGuestRoomLinkResult.Failure(it.value)
            }
        }
    }
}
