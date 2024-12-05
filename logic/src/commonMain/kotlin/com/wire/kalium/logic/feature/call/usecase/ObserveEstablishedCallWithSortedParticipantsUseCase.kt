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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Use case to observe established call with the participants sorted according to the [CallingParticipantsOrder]
 */
interface ObserveEstablishedCallWithSortedParticipantsUseCase {
    suspend operator fun invoke(conversationId: ConversationId): Flow<Call?>
}

class ObserveEstablishedCallWithSortedParticipantsUseCaseImpl internal constructor(
    private val callRepository: CallRepository,
    private val callingParticipantsOrder: CallingParticipantsOrder
) : ObserveEstablishedCallWithSortedParticipantsUseCase {

    override suspend operator fun invoke(conversationId: ConversationId): Flow<Call?> {
        return callRepository.observeCurrentCall(conversationId)
            .distinctUntilChanged()
            .map { call ->
                call?.let {
                    val sortedParticipants = callingParticipantsOrder.reorderItems(call.participants)
                    call.copy(participants = sortedParticipants)
                }
            }
    }
}
