package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.feature.call.CallManager

class MuteCallUseCase(private val callManager: Lazy<CallManager>) {

    suspend operator fun invoke() {
        callManager.value.muteCall(true)
    }
}
