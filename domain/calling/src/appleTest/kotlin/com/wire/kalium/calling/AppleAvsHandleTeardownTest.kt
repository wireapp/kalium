/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.wire.kalium.calling

import kotlinx.cinterop.COpaquePointer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import platform.CoreFoundation.CFRunLoopRunInMode
import platform.CoreFoundation.kCFRunLoopDefaultMode
import platform.Foundation.NSLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class AppleAvsHandleTeardownTest {
    @Test
    fun nativeShutdownDrainsCallbacksAndAllowsSameIdentityToReopen() = runTest {
        val teardown = async(Dispatchers.Default) {
            withTimeout(15_000) {
                assertTrue(AppleAvsFlowManager().startIfAvailable())
                val bridge = AppleAvs.bridge
                val identity = "${Uuid.random()}@example.com"
                val first = RecordingCallbacks()
                val oldHandle = assertNotNull(bridge.userHandle(identity, "abcd", first))
                first.configRequested.await()
                coroutineScope {
                    List(4) { async { bridge.releaseUserHandle(oldHandle) } }.awaitAll()
                }
                val oldLogs = first.logCount()
                val second = RecordingCallbacks()
                val newHandle = assertNotNull(bridge.userHandle(identity, "abcd", second))
                try {
                    second.configRequested.await()
                    assertNotEquals(oldHandle, newHandle)
                    assertEquals(oldLogs, first.logCount())
                    // A repeated release of the old identity must not destroy its new handle.
                    bridge.releaseUserHandle(oldHandle)
                    assertEquals(newHandle, bridge.userHandle(identity, "abcd", second))
                } finally {
                    bridge.releaseUserHandle(newHandle)
                }
            }
        }
        // AVS initializes iOS media services on the main queue while the worker owns the handle.
        while (!teardown.isCompleted) {
            CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.01, true)
        }
        teardown.await()
    }

    private class RecordingCallbacks : AppleAvsCallbacks {
        val configRequested = CompletableDeferred<Unit>()
        private val lock = NSLock()
        private var logCalls = 0
        fun logCount(): Int {
            lock.lock()
            return try { logCalls } finally { lock.unlock() }
        }
        override fun onReady(version: Int) = Unit
        override fun onLog(level: Int, message: String) {
            lock.lock()
            try { logCalls++ } finally { lock.unlock() }
        }
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
        override fun onConfigRequest(handle: UInt, context: COpaquePointer?): Int {
            configRequested.complete(Unit)
            return 0
        }
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
