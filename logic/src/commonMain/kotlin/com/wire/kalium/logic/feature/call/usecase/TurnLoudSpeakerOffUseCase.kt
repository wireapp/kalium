package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.feature.call.MediaManagerService

/**
 * This use case is responsible for setting the speaker state to off.
 */
class TurnLoudSpeakerOffUseCase(
    private val mediaManagerService: MediaManagerService) {
    operator fun invoke() {
        mediaManagerService.turnLoudSpeakerOff()
    }
}
