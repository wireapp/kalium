package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow

actual class MediaManagerServiceImpl : MediaManagerService {
    override fun turnLoudSpeakerOn() {
        kaliumLogger.w("turnLoudSpeakerOn for JVM but not supported yet.")
    }

    override fun turnLoudSpeakerOff() {
        kaliumLogger.w("turnLoudSpeakerOff for JVM but not supported yet.")
    }

    override fun observeSpeaker(): Flow<Boolean> {
        kaliumLogger.w("observeSpeaker for JVM but not supported yet.")
    }
}
