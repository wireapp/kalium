/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.nse

import com.wire.kalium.common.logger.CoreLogger
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.AppleCallTrace
import com.wire.kalium.logger.CallTraceEvent
import com.wire.kalium.logger.KaliumLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KaliumNSELoggingTest {
    @Test
    fun processConfigurationReusesTheWriterAndRefreshesOptIn() = runTest {
        // Dispatch queues use real time; do not advance the timeout with the test virtual clock.
        withContext(Dispatchers.Default) {
            val root = (NSTemporaryDirectory() + NSUUID().UUIDString).toPath()
            val logger = NSEProcessLogger()
            try {
                assertTrue(logger.configure(root.toString(), false))
                kaliumLogger.i("disabled-entry")
                logger.recordNative("native-disabled", false)
                assertTrue(logger.flush(2_000))
                assertFalse(FileSystem.SYSTEM.exists(root / "logs" / "wire_logs-nse.log"))

                assertTrue(logger.configure(root.toString(), true))
                kaliumLogger.i("first-entry")
                AppleCallTrace.record(CallTraceEvent.NSE_INCOMING)
                logger.recordNative("native-received", false)
                logger.recordNative("native-failed", true)
                assertTrue(logger.flush(2_000))
                assertTrue(logger.configure(root.toString(), true))
                kaliumLogger.v("second-entry")
                assertTrue(logger.flush(2_000))
                assertTrue(logger.configure(root.toString(), false))
                kaliumLogger.i("ignored-entry")
                logger.recordNative("native-ignored", true)
                assertFalse(logger.configure((root / "different").toString(), true))
                kaliumLogger.i("wrong-root-entry")
                assertTrue(logger.flush(2_000))

                val text = FileSystem.SYSTEM.read(root / "logs" / "wire_logs-nse.log") { readUtf8() }
                assertEquals(1, text.lines().count { "first-entry" in it })
                assertTrue(text.lines().any { "role=NSE" in it && "event=NSE_INCOMING" in it })
                assertTrue(FileSystem.SYSTEM.exists(root / "logs" / "wire_logs-nse.coverage.json"))
                val verbose = text.lines().single { "second-entry" in it }
                kotlin.time.Instant.parse(verbose.substringBefore(' '))
                assertTrue(verbose.substringAfter(' ').startsWith("Verbose: ("))
                assertEquals(1, text.lines().count { "Info: (Notifications) native-received" in it })
                assertEquals(1, text.lines().count { "Error: (Notifications) native-failed" in it })
                assertFalse("native-disabled" in text)
                assertFalse("native-ignored" in text)
                assertFalse("disabled-entry" in text)
                assertFalse("ignored-entry" in text)
                assertFalse("wrong-root-entry" in text)
            } finally {
                logger.configure(root.toString(), false)
                logger.flush(2_000)
                CoreLogger.init(KaliumLogger.Config.DISABLED)
                FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
            }
        }
    }
}
