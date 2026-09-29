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

package com.wire.kalium.logger

import co.touchlab.kermit.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppleDiagnosticLogProcessTest {
    @Test
    fun independentProcessLockDropsWritesAndRejectsExportUntilReleased() = runTest {
        val fs = FileSystem.SYSTEM
        val root = (NSTemporaryDirectory() + "/logger-process-" + NSUUID().UUIDString).toPath()
        val writer = AppleDiagnosticLogWriter(root.toString(), "nse", true)
        writer.flushLogs()
        val process = DiagnosticLockProcess.start((root / AppleDiagnosticLogStorage.LOCK_FILE_NAME).toString())
        try {
            writer.log(Severity.Info, "contended", "test", null)
            assertTrue(withContext(Dispatchers.Default) { writer.flushLogsWithin(1_000) })
            assertFalse(fs.exists(root / "logs" / "wire_logs-nse.log"))
            assertFailsWith<IOException> { writer.createLogsSnapshot((root / "snapshot").toString()) }
            assertFailsWith<IOException> { writer.deleteLogs() }
            process.stop()
            writer.log(Severity.Info, "after-release", "test", null)
            writer.flushLogs()
            assertTrue("after-release" in fs.read(root / "logs" / "wire_logs-nse.log") { readUtf8() })
        } finally {
            process.stop()
            fs.deleteRecursively(root)
        }
    }
}
