package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.feature.call.MediaManagerService
import kotlinx.coroutines.flow.Flow

/**
 * This use case is responsible for observing the speaker state, returns true if the speaker is on, false otherwise.
 */
class ObserveSpeakerUseCase(private val mediaManagerService: MediaManagerService) {

    operator fun invoke(): Flow<Boolean> = mediaManagerService.observeSpeaker()
}
