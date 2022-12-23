package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.feature.call.MediaManagerService

/**
 * This use case is responsible for turning the loudspeaker on for a call.
 */
class TurnLoudSpeakerOnUseCase(private val mediaManagerService: MediaManagerService) {
    operator fun invoke() {
        mediaManagerService.turnLoudSpeakerOn()
    }
}
