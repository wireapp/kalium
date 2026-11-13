/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.wire.kalium.calling

import kotlinx.cinterop.COpaquePointer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.CoreFoundation.CFRunLoopRunInMode
import platform.CoreFoundation.kCFRunLoopDefaultMode
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIView
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class AppleAvsMediaRuntimeTest {
    @Test
    fun activationAfterVideoAndLastHandleReleaseKeepsTheMediaSystem() = runTest {
        val work = async(Dispatchers.Default) {
            withTimeout(20_000) {
                val bridge = AppleAvs.bridge
                val identity = "${Uuid.random()}@example.com"
                val first = ConfigCallbacks()
                val handle = assertNotNull(bridge.userHandle(identity, "abcd", first))
                first.configRequested.await()
                withContext(Dispatchers.Main) {
                    val preview = UIView()
                    val video = AppleAvsFlowManager()
                    assertTrue(video.attachLocalVideoPreview(preview))
                    assertTrue(video.detachLocalVideoPreview(preview))
                }
                bridge.releaseUserHandle(handle)
                // A second AVS loop dropped the process media system, so this activation read freed AVS state.
                withContext(Dispatchers.Main) {
                    NSNotificationCenter.defaultCenter.postNotificationName(UIApplicationDidBecomeActiveNotification, null)
                }
                delay(500)
                val second = ConfigCallbacks()
                val reopened = assertNotNull(bridge.userHandle(identity, "abcd", second))
                second.configRequested.await()
                bridge.releaseUserHandle(reopened)
            }
        }
        // AVS runs media work on the main queue.
        while (!work.isCompleted) {
            CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.01, true)
        }
        work.await()
    }

    private class ConfigCallbacks : AppleAvsCallbacks {
        val configRequested = CompletableDeferred<Unit>()
        override fun onConfigRequest(handle: UInt, context: COpaquePointer?): Int {
            configRequested.complete(Unit)
            return 0
        }
        override fun onReady(version: Int) = Unit
        override fun onLog(level: Int, message: String) = Unit
        override fun onSend(
            context: COpaquePointer?,
            conversationId: String?,
            selfUserId: String?,
            selfClientId: String?,
            targetRecipientsJson: String?,
            clientIdDestination: String?,
            data: ByteArray,
            transient: Boolean,
            myClientsOnly: Boolean
        ): Int = 0
        override fun onSftRequest(context: COpaquePointer?, url: String?, data: ByteArray): Int = 0
        override fun onIncomingCall(
            conversationId: String?,
            messageTime: UInt,
            userId: String?,
            clientId: String?,
            video: Boolean,
            shouldRing: Boolean,
            conversationType: Int
        ) = Unit
        override fun onMissedCall(conversationId: String?, messageTime: UInt, userId: String?, video: Boolean) = Unit
        override fun onAnsweredCall(conversationId: String?) = Unit
        override fun onEstablishedCall(conversationId: String?, userId: String?, clientId: String?) = Unit
        override fun onClosedCall(reason: Int, conversationId: String?, messageTime: UInt, userId: String?, clientId: String?) = Unit
        override fun onMetrics(conversationId: String?, metricsJson: String?) = Unit
        override fun onAudioCbrChanged(userId: String?, clientId: String?, enabled: Boolean) = Unit
        override fun onVideoStateChanged(conversationId: String?, userId: String?, clientId: String?, state: Int) = Unit
        override fun onParticipantChanged(conversationId: String?, data: String?) = Unit
        override fun onNetworkQualityChanged(conversationId: String?, userId: String?, clientId: String?, qualityInfoJson: String?) = Unit
        override fun onRequestNewEpoch(handle: UInt, conversationId: String?) = Unit
        override fun onClientsRequest(handle: UInt, conversationId: String?) = Unit
        override fun onActiveSpeakersChanged(handle: UInt, conversationId: String?, data: String?) = Unit
        override fun onMuteStateChanged(isMuted: Boolean) = Unit
    }
}
