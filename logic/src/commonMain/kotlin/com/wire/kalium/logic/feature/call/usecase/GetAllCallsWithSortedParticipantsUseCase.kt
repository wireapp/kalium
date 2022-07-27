package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.ParticipantsOrder
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetAllCallsWithSortedParticipantsUseCase internal constructor(
    private val callRepository: CallRepository,
    private val participantsOrder: ParticipantsOrder
) {
    suspend operator fun invoke(): Flow<List<Call>> {
        return callRepository.callsFlow().map { calls ->
            calls.map { call ->
                val sortedParticipants = participantsOrder.reorderItems(call.participants)
                call.copy(participants = sortedParticipants)
            }
        }
    }
}
