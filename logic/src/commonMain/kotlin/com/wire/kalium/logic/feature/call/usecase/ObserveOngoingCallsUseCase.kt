package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.Call
import kotlinx.coroutines.flow.Flow

interface ObserveOngoingCallsUseCase {
    suspend operator fun invoke(): Flow<List<Call>>
}

/**
 *
 * @param callRepository CallRepository for getting all the ongoing calls.
 *
 * @return Flow<List<Call>> - Flow of Calls List that should be shown to the user.
 * That Flow emits everytime when the list is changed
 */
internal class ObserveOngoingCallsUseCaseImpl(
    private val callRepository: CallRepository
) : ObserveOngoingCallsUseCase {

    override suspend fun invoke(): Flow<List<Call>> {
        return callRepository.ongoingCallsFlow()
    }
}
