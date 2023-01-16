package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.feature.call.MediaManagerService
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for observing the speaker state, returns true if the speaker is on, false otherwise.
 */
class ObserveSpeakerUseCase(private val mediaManagerService: MediaManagerService) {

    suspend operator fun invoke(): Flow<Boolean> = withContext(KaliumDispatcherImpl.default) {
        mediaManagerService.observeSpeaker()
    }
}
