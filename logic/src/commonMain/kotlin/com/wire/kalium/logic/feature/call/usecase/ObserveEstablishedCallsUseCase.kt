package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.Call
import kotlinx.coroutines.flow.Flow

/**
 * This use case is responsible for observing the established calls.
 */
class ObserveEstablishedCallsUseCase internal constructor(
    private val callRepository: CallRepository,
) {
    /**
     * That Flow emits everytime when the list is changed
     * @return a [Flow] of the list of established calls that should be shown to the user.
     */
    suspend operator fun invoke(): Flow<List<Call>> {
        return callRepository.establishedCallsFlow()
    }
}
