/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature

import com.wire.kalium.logic.util.PlatformView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.NSThread
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class LocalVideoPreviewAttachmentTest {
    @Test
    fun publicCaptureAndDetachDispatchToMainWhenCalledFromBackground() = runTest {
        assertTrue(NSThread.isMainThread, "The test must pump the main run loop")
        val events = mutableListOf<String>()
        fun recordOnMain(event: String) {
            assertTrue(NSThread.isMainThread, "$event must execute on the main thread")
            events += event
        }
        val owner = LocalVideoPreviewAttachmentOwner(
            attachNative = { true },
            detachNative = { recordOnMain("detach"); true },
            startNativeCapture = { recordOnMain("start") },
            stopNativeCapture = { recordOnMain("stop") },
        )
        val attachment = owner.attach(PlatformView(null))
        val work = async(Dispatchers.Default) {
            assertFalse(NSThread.isMainThread, "The caller must run on a background thread")
            attachment.startCapture()
            attachment.stopCapture()
            attachment.startCapture()
            attachment.detach()
        }

        // Keep the real Darwin main queue running while the background caller awaits it.
        val started = TimeSource.Monotonic.markNow()
        while (!work.isCompleted && started.elapsedNow() < 10.seconds) {
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
        }
        assertTrue(work.isCompleted, "Preview operations did not finish on the main queue")
        work.await()
        assertEquals(listOf("start", "stop", "start", "stop", "detach"), events)
    }

    @Test
    fun captureStartsOnlyWhenRequestedAndReleaseStopsBeforeDetaching() {
        val events = mutableListOf<String>()
        val owner = owner(events)
        val view = PlatformView(null)

        val attachment = owner.attach(view)
        assertSame(attachment, owner.attach(view))
        owner.startCapture(attachment)
        owner.startCapture(attachment)
        owner.stopCapture(attachment)
        owner.stopCapture(attachment)
        owner.startCapture(attachment)
        owner.detach(attachment)
        owner.detach(attachment)

        assertEquals(listOf("attach", "start", "stop", "start", "stop", "detach"), events)
    }

    @Test
    fun replacementStopsAndDetachesOldPreviewBeforeAttachingNewOne() {
        val events = mutableListOf<String>()
        val owner = owner(events)
        val old = owner.attach(PlatformView(null))
        owner.startCapture(old)

        val replacement = owner.attach(PlatformView(null))
        owner.stopCapture(old)
        owner.detach(old)
        owner.startCapture(old)
        owner.startCapture(replacement)
        owner.detach(replacement)

        assertEquals(listOf("attach", "start", "stop", "detach", "attach", "start", "stop", "detach"), events)
    }

    private fun owner(events: MutableList<String>) = LocalVideoPreviewAttachmentOwner(
        attachNative = { events += "attach"; true },
        detachNative = { events += "detach"; true },
        startNativeCapture = { events += "start" },
        stopNativeCapture = { events += "stop" },
    )
}
