/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.wire.kalium.calling

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
                val first = AppleAvsHandleTeardownTest.RecordingCallbacks()
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
                val second = AppleAvsHandleTeardownTest.RecordingCallbacks()
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
}
