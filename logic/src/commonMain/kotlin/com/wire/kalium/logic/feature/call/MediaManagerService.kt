package com.wire.kalium.logic.feature.call

import kotlinx.coroutines.flow.Flow

interface MediaManagerService {
    fun turnLoudSpeakerOn()
    fun turnLoudSpeakerOff()
    fun observeSpeaker(): Flow<Boolean>
}

expect class MediaManagerServiceImpl : MediaManagerService
