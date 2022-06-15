package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.feature.call.MediaManagerService

class TurnLoudSpeakerOnUseCase(private val mediaManagerService: MediaManagerService) {
    operator fun invoke() {
        mediaManagerService.turnLoudSpeakerOn()
    }
}
