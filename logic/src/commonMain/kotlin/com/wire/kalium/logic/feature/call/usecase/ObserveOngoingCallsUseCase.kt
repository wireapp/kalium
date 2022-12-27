package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.Call
import kotlinx.coroutines.flow.Flow

/**
 * This use case is responsible for observing the ongoing calls.
 */
interface ObserveOngoingCallsUseCase {
    /**
     * That Flow emits everytime when the list is changed
     * @return a [Flow] of the list of ongoing calls that should be shown to the user.
     */
    suspend operator fun invoke(): Flow<List<Call>>
}

internal class ObserveOngoingCallsUseCaseImpl(
    private val callRepository: CallRepository
) : ObserveOngoingCallsUseCase {

    override suspend fun invoke(): Flow<List<Call>> {
        return callRepository.ongoingCallsFlow()
    }
}
