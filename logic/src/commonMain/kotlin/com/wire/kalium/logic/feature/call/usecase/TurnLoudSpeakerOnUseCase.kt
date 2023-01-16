package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.feature.call.MediaManagerService
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for turning the loudspeaker on for a call.
 */
class TurnLoudSpeakerOnUseCase(
    private val mediaManagerService: MediaManagerService,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {
    suspend operator fun invoke() = withContext(dispatcher.default) {
        mediaManagerService.turnLoudSpeakerOn()
    }
}
