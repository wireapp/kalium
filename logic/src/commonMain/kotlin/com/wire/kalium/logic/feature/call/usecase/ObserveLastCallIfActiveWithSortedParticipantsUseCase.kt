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
 * Use case to observe the last call for the given [ConversationId] if it's state is active - if last active call is already inactive
 * or there's not last active call, then returns null - with sorted participants according to the [CallingParticipantsOrder].
 * The call is active when it's one of the following states: STARTED, INCOMING, ANSWERED, ESTABLISHED, STILL_ONGOING.
 */
interface ObserveLastCallIfActiveWithSortedParticipantsUseCase {
    suspend operator fun invoke(conversationId: ConversationId): Flow<Call?>
}

class ObserveLastCallIfActiveWithSortedParticipantsUseCaseImpl internal constructor(
    private val callRepository: CallRepository,
    private val callingParticipantsOrder: CallingParticipantsOrder
) : ObserveLastCallIfActiveWithSortedParticipantsUseCase {

    override suspend operator fun invoke(conversationId: ConversationId): Flow<Call?> =
        callRepository.observeLastCallIfActiveByConversationId(conversationId).map { call ->
            call?.let {
                val sortedParticipants = callingParticipantsOrder.reorderItems(call.participants)
                call.copy(participants = sortedParticipants)
            }
        }
}
