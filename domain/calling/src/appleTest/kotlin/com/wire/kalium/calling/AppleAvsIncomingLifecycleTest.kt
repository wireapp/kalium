/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.wire.kalium.calling

import kotlinx.cinterop.COpaquePointer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreFoundation.CFRunLoopRunInMode
import platform.CoreFoundation.kCFRunLoopDefaultMode
import platform.Foundation.NSLock
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AppleAvsIncomingLifecycleTest {
    @Test
    fun signalingEngineTermination() = runTest {
        val work = async(Dispatchers.Default) {
            withTimeout(90_000) {
                assertTrue(AppleAvsFlowManager().startIfAvailable())
                val bridge = AppleAvs.bridge
                for (type in 0..3) for (termination in listOf("answer", "reject", "cancel")) {
                    val self = "${Uuid.random()}@example.com"
                    val peer = "${Uuid.random()}@example.com"
                    val conversation = "11111111-1111-4111-8111-111111111111@example.com"
                    val callbacks = RecordingCallbacks()
                    // Exercise real signaling without unrelated asynchronous iOS audio teardown.
                    val handle = assertNotNull(signalingUserHandle(self, "abcd", callbacks))
                    try {
                        withTimeout(5000) { callbacks.ready.await() }
                        val startType = when (type) { 0 -> "SETUP"; 1 -> "GROUPSTART"; else -> "CONFSTART" }
                        val now = Clock.System.now().epochSeconds.toUInt()
                        fun receive(payload: String, sender: String = peer, client: String = "def0", time: UInt = now) {
                            assertTrue(bridge.receiveCallingMessage(handle, payload.encodeToByteArray(), maxOf(now, time), time,
                                conversation, sender, client, type, false))
                        }
                        val session = if (type >= 2) "6572f49303d3df266ecfac1d82c74b7c" else "call"
                        receive(signal(startType, session = session))
                        val incoming = withTimeoutOrNull(3000) { callbacks.events.receive() }
                        println("FULL_ENGINE type=$type termination=$termination initial=$incoming")
                        assertNotNull(incoming, "No incoming callback for type=$type")
                        val endType = when (termination) {
                            "answer" -> startType
                            "reject" -> "REJECT"
                            else -> when (type) { 0 -> "CANCEL"; 1 -> "GROUPLEAVE"; else -> "CONFEND" }
                        }
                        receive(signal(endType, termination == "answer", if (type == 0 && termination == "answer") "other" else session),
                            if (termination == "cancel") peer else self,
                            if (termination == "cancel") "def0" else "eeee", now + 1u)
                        val ended = withTimeoutOrNull(3000) { callbacks.events.receive() }
                        println("FULL_ENGINE type=$type termination=$termination final=$ended")
                        val reason = when {
                            termination == "cancel" && type >= 2 -> 0
                            termination == "cancel" -> 4
                            termination == "answer" && type >= 2 -> 5
                            type != 0 -> 7
                            termination == "answer" -> 5
                            else -> 10
                        }
                        kotlin.test.assertEquals("incoming:true", incoming)
                        kotlin.test.assertEquals("closed:$reason", ended, "type=$type termination=$termination")
                        if (type == 0 && termination == "answer") {
                            receive(signal("SETUP", session = "new-call"), time = now + 2u)
                            val restarted = withTimeoutOrNull(3000) { callbacks.events.receive() }
                            println("FULL_ENGINE successive_start=$restarted")
                            kotlin.test.assertEquals("incoming:true", restarted)
                            // AVS applies same-account REJECT before checking the peer session.
                            // Demonstrate why the host must reject a delayed older backend event.
                            receive(signal("REJECT", session = "call"), self, "eeee", now + 1u)
                            val delayed = withTimeoutOrNull(3000) { callbacks.events.receive() }
                            println("FULL_ENGINE delayed_reject=$delayed")
                            kotlin.test.assertEquals("closed:10", delayed)
                            println("FULL_ENGINE delayed_old_reject_closes_new_call=true")
                        }
                    } finally {
                        bridge.releaseUserHandle(handle)
                    }
                }
            }
        }
        while (!work.isCompleted) CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.01, true)
        work.await()
    }

    private fun signal(type: String, response: Boolean = false, session: String = "call"): String =
        """{"version":"3.0","type":"$type","sessid":"$session","resp":$response,"sdp":"v=0", "props":{"videosend":"false"},"sft_url":"https://sft.invalid","secret":"YWJjZA==","timestamp":"1","seqno":"1"}"""

    private class RecordingCallbacks : AppleAvsCallbacks {
        val ready = CompletableDeferred<Unit>()
        val events = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        private val lock = NSLock()
        private var logCalls = 0
        fun logCount(): Int {
            lock.lock()
            return try { logCalls } finally { lock.unlock() }
        }
        override fun onReady(version: Int) { ready.complete(Unit) }
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
        ) { events.trySend("incoming:$shouldRing") }
        override fun onMissedCall(conversationId: String?, messageTime: UInt, userId: String?, video: Boolean) = Unit
        override fun onAnsweredCall(conversationId: String?) = Unit
        override fun onEstablishedCall(conversationId: String?, userId: String?, clientId: String?) = Unit
        override fun onClosedCall(reason: Int, conversationId: String?, messageTime: UInt, userId: String?, clientId: String?) { events.trySend("closed:$reason") }
        override fun onMetrics(conversationId: String?, metricsJson: String?) = Unit
        override fun onConfigRequest(handle: UInt, context: COpaquePointer?): Int {
            AppleAvs.bridge.updateConfig(handle, 0, """{"ice_servers":[{"urls":["stun:127.0.0.1:9"]}],"sft_servers":[{"urls":["https://sft.invalid"]}],"ttl":3600}""")
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
