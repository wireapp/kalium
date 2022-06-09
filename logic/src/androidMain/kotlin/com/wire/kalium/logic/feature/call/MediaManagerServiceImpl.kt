package com.wire.kalium.logic.feature.call

import android.content.Context
import com.waz.media.manager.MediaManager
import com.waz.media.manager.MediaManagerListener
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class MediaManagerServiceImpl(
    appContext: Context
) : MediaManagerService {

    private val mediaManager: MediaManager = MediaManager.getInstance(appContext).apply {
        addListener(object : MediaManagerListener {
            override fun onPlaybackRouteChanged(route: Int) {
                _isLoudSpeakerOnFlow.value = this@apply.isLoudSpeakerOn
                kaliumLogger.w("onPlaybackRouteChanged called with route = $route..") //Nothing to do for now
            }

            // we don't need to do anything in here, I guess, and the return value gets ignored anyway
            override fun mediaCategoryChanged(conversationId: String?, category: Int): Int = category
        })
    }

    private val _isLoudSpeakerOnFlow = MutableStateFlow(mediaManager.isLoudSpeakerOn)
    private val isLoudSpeakerOnFlow = _isLoudSpeakerOnFlow.asStateFlow()

    override fun turnLoudSpeakerOn() {
        mediaManager.turnLoudSpeakerOn()
    }

    override fun turnLoudSpeakerOff() {
        mediaManager.turnLoudSpeakerOff()
    }

    override fun observeSpeaker(): Flow<Boolean> = isLoudSpeakerOnFlow
}
