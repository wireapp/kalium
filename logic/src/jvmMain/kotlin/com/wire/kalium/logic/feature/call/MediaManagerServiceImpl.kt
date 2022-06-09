package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

actual class MediaManagerServiceImpl : MediaManagerService {
    override fun turnLoudSpeakerOn() {
        kaliumLogger.w("turnLoudSpeakerOn for JVM but not supported yet.")
    }

    override fun turnLoudSpeakerOff() {
        kaliumLogger.w("turnLoudSpeakerOff for JVM but not supported yet.")
    }

    override fun observeSpeaker(): StateFlow<Boolean> {
        kaliumLogger.w("observeSpeaker for JVM but not supported yet.")
        return MutableStateFlow(false)
    }
}
