package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.feature.call.MediaManagerService
import kotlinx.coroutines.flow.Flow

class ObserveSpeakerUseCase(private val mediaManagerService: MediaManagerService) {

    operator fun invoke(): Flow<Boolean> = mediaManagerService.observeSpeaker()
}
