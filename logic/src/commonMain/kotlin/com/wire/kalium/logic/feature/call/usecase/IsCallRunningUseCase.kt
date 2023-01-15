package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.feature.call.usecase.IsCallRunningUseCase.Companion.runningCalls
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * This use case is responsible for checking if there is a call running.
 * @see [runningCalls]
 */
class IsCallRunningUseCase internal constructor(
    private val callRepository: CallRepository
) {
    /**
     * @return true if there is a call running, false otherwise.
     */
    suspend operator fun invoke(): Boolean {
        return callRepository.callsFlow().map { calls ->
            val call = calls.find {
                it.status in runningCalls
            }
            call != null
        }.first()
    }

    companion object {
        val runningCalls = listOf(CallStatus.STARTED, CallStatus.INCOMING, CallStatus.ESTABLISHED)
    }
}
