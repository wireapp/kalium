package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallRepository

class CallScope(
    private val callRepository: CallRepository
) {
    val getCallConfigUseCase: GetCallConfigUseCase get() = GetCallConfigUseCaseImpl(
        callRepository = callRepository
    )
}
