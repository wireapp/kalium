package com.wire.kalium.logic.feature.call

import kotlinx.coroutines.flow.StateFlow

interface MediaManagerService {
    fun turnLoudSpeakerOn()
    fun turnLoudSpeakerOff()
    fun observeSpeaker(): StateFlow<Boolean>
}

expect class MediaManagerServiceImpl : MediaManagerService
