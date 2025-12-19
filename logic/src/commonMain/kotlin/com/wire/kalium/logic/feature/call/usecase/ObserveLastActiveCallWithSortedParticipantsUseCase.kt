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

import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallingParticipantsOrder
import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Use case to observe the last active call for the given [ConversationId] or null if there is no such call,
 * with sorted participants according to the [CallingParticipantsOrder].
 * The call is active when it's one of the following states: STARTED, INCOMING, ANSWERED, ESTABLISHED, STILL_ONGOING.
 */
public interface ObserveLastActiveCallWithSortedParticipantsUseCase {
    public suspend operator fun invoke(conversationId: ConversationId): Flow<Call?>
}

internal class ObserveLastActiveCallWithSortedParticipantsUseCaseImpl internal constructor(
    private val callRepository: CallRepository,
    private val callingParticipantsOrder: CallingParticipantsOrder
) : ObserveLastActiveCallWithSortedParticipantsUseCase {

    override suspend operator fun invoke(conversationId: ConversationId): Flow<Call?> =
        callRepository.observeLastActiveCallByConversationId(conversationId).map { call ->
            call?.let {
                val sortedParticipants = callingParticipantsOrder.reorderItems(call.participants)
                call.copy(participants = sortedParticipants)
            }
        }
}
