/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.wire.kalium.calling

import avs.AVSMediaManager
import avs.AVSMediaManagerChangeNotification
import avs.AVSMediaManagerObserverProtocol
import avs.AVSPlaybackRoute
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.darwin.NSObject

actual class AppleAvsSpeaker actual constructor() {
    actual fun setEnabled(enabled: Boolean) {
        manager().enableSpeaker(enabled)
    }

    actual fun observeLoudspeaker(): Flow<Boolean> = callbackFlow {
        val manager = manager()
        val observer = object : NSObject(), AVSMediaManagerObserverProtocol {
            override fun avsMediaManagerDidChange(notification: AVSMediaManagerChangeNotification?) {
                if (notification?.playbackRouteChanged == true && notification.avsMediaManager == manager) {
                    trySend(manager.playbackRoute == AVSPlaybackRoute.AVSPlaybackRouteSpeaker)
                }
            }
        }
        AVSMediaManagerChangeNotification.addObserver(observer)
        trySend(manager.playbackRoute == AVSPlaybackRoute.AVSPlaybackRouteSpeaker)
        awaitClose { AVSMediaManagerChangeNotification.removeObserver(observer) }
    }

    private fun manager(): AVSMediaManager {
        check(AppleAvs.bridge.startIfAvailable()) { "AVS is unavailable" }
        return checkNotNull(AVSMediaManager.defaultMediaManager()) { "AVS media manager is unavailable" }
    }
}
