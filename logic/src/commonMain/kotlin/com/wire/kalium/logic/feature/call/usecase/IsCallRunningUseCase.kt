package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.CallStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class IsCallRunningUseCase(
    private val callRepository: CallRepository
) {
    suspend operator fun invoke(): Boolean {
        return callRepository.callsFlow().map { calls ->
            val call = calls.find {
                it.status in listOf(CallStatus.STARTED, CallStatus.INCOMING, CallStatus.ESTABLISHED)
            }
            call != null
        }.first()
    }
}
