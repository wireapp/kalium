package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallingParticipantsOrder
import com.wire.kalium.logic.feature.call.Call
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Use case to get a list of all calls with the participants sorted according to the [CallingParticipantsOrder]
 */
class GetAllCallsWithSortedParticipantsUseCase internal constructor(
    private val callRepository: CallRepository,
    private val callingParticipantsOrder: CallingParticipantsOrder
) {
    /**
     * Observes a [Flow] list of all calls with the participants sorted according to the [CallingParticipantsOrder]
     */
    suspend operator fun invoke(): Flow<List<Call>> {
        return callRepository.callsFlow().map { calls ->
            calls.map { call ->
                val sortedParticipants = callingParticipantsOrder.reorderItems(call.participants)
                call.copy(participants = sortedParticipants)
            }
        }
    }
}
