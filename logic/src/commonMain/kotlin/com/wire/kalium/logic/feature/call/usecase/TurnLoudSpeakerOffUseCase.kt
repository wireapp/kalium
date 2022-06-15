package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.feature.call.MediaManagerService

class TurnLoudSpeakerOffUseCase(private val mediaManagerService: MediaManagerService) {
    operator fun invoke() {
        mediaManagerService.turnLoudSpeakerOff()
    }
}
