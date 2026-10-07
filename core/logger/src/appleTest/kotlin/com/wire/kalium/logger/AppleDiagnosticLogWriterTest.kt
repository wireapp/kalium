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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import platform.Foundation.NSCondition
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSThread
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppleDiagnosticLogWriterTest {
    private val fs = FileSystem.SYSTEM

    @Test
    fun optOutPreservesAcceptedEntriesAndExactRecordFormat() = runTest {
        fixture { root ->
            val writer = AppleDiagnosticLogWriter(root.toString())
            writer.log(Severity.Info, "disabled", "test", null)
            writer.flushLogs()
            assertFalse(fs.exists(root / "logs" / "wire_logs.log"))
            writer.updateLoggingEnabled(true)
            val failure = IllegalStateException("fixture")
            writer.log(Severity.Warn, "accepted", "test", failure)
            writer.setLoggingEnabled(false)
            writer.log(Severity.Info, "ignored", "test", null)
            writer.flushLogs()
            val contents = fs.read(root / "logs" / "wire_logs.log") { readUtf8() }
            val timestamp = contents.substringBefore(' ')
            kotlin.time.Instant.parse(timestamp)
            assertEquals("Warn: (test) accepted\n" + failure.stackTraceToString() + "\n", contents.substringAfter(' '))
            assertFalse("ignored" in contents)
            val coverage = fs.read(root / "logs" / "wire_logs.coverage.json") { readUtf8() }
            assertTrue("\"disabled\":2" in coverage)
            assertTrue("\"written\":1" in coverage)
            assertFalse(AppleDiagnosticLogWriter(root.toString()).isLoggingEnabled)
        }
    }

    @Test
    fun bothStreamsRotateAndSnapshotsStayImmutable() = runTest {
        fixture { root ->
            val app = writer(root, "app", maxFileBytes = 1)
            val nse = writer(root, "nse", maxFileBytes = 1)
            repeat(13) { index ->
                app.log(Severity.Info, "app-$index", "fixture", null)
                app.flushLogs()
                nse.log(Severity.Info, "nse-$index", "fixture", null)
                nse.flushLogs()
            }
            val snapshot = root / "snapshot"
            app.createLogsSnapshot(snapshot.toString())
            assertEquals(24, fs.list(snapshot).size)
            assertTrue(fs.exists(snapshot / "wire_logs-nse-10.log"))
            assertTrue(fs.read(snapshot / "wire_logs.log") { readUtf8() }.contains("app-12"))
            app.log(Severity.Info, "later", "fixture", null)
            app.flushLogs()
            nse.deleteLogs()
            assertTrue(fs.list(root / "logs").isEmpty())
            assertTrue(fs.read(snapshot / "wire_logs.log") { readUtf8() }.contains("app-12"))
            assertTrue(fs.exists(root / AppleDiagnosticLogStorage.LOCK_FILE_NAME))
        }
    }

    @Test
    fun snapshotFiltersUnrelatedFilesDirectoriesTemporaryFilesAndSymlinks() = runTest {
        fixture { root ->
            val writer = writer(root)
            writer.log(Severity.Info, "record", "test", null)
            writer.flushLogs()
            val logs = root / "logs"
            fs.write(logs / "wire_logs-nse.log.tmp") { writeUtf8("temporary") }
            fs.write(logs / "unrelated.txt") { writeUtf8("private") }
            fs.createDirectories(logs / "wire_logs-dir.log")
            fs.createSymlink(logs / "wire_logs-link.log", logs / "wire_logs.log")
            writer.createLogsSnapshot((root / "snapshot").toString())
            assertEquals(listOf("wire_logs.coverage.json", "wire_logs.log"), fs.list(root / "snapshot").map { it.name }.sorted())
            writer.deleteLogs()
            assertTrue(fs.exists(logs / "unrelated.txt"))
            assertTrue(fs.metadata(logs / "wire_logs-link.log").symlinkTarget != null)
        }
    }

    @Test
    fun failedSnapshotCleansUpButNeverOverwritesExistingDestination() = runTest {
        fixture { root ->
            val writer = writer(root)
            writer.log(Severity.Info, "record", "test", null)
            writer.flushLogs()
            val failing = object : ForwardingFileSystem(fs) {
                override fun source(file: Path): Source = throw IOException("fixture copy failure")
            }
            val exporter = writer(root, fileSystem = failing)
            val snapshot = root / "snapshot"
            assertFailsWith<IOException> { exporter.createLogsSnapshot(snapshot.toString()) }
            assertFalse(fs.exists(snapshot))
            fs.createDirectories(snapshot)
            fs.write(snapshot / "preserved") { writeUtf8("keep") }
            assertFailsWith<IOException> { writer.createLogsSnapshot(snapshot.toString()) }
            assertEquals("keep", fs.read(snapshot / "preserved") { readUtf8() })
        }
    }

    @Test
    fun failedStorageDoesNotEscapeWriterAndCanRecoverWhileDisabled() = runTest {
        fixture { root ->
            val writer = writer(root)
            fs.write(root / "logs") { writeUtf8("not a directory") }
            writer.log(Severity.Info, "dropped", "test", null)
            writer.setLoggingEnabled(false)
            assertFailsWith<IOException> { writer.flushLogs() }
            fs.delete(root / "logs")
            writer.createLogsSnapshot((root / "snapshot").toString())
            assertEquals(listOf("wire_logs.coverage.json"), fs.list(root / "snapshot").map { it.name })
        }
    }

    @Test
    fun snapshotContentionDropsExtensionWritesAndCompletionRemainsBounded() = runTest {
        fixture { root ->
            val blocking = BlockingCopyFileSystem()
            val app = writer(root, fileSystem = blocking)
            val nse = writer(root, "nse")
            app.log(Severity.Info, "before-copy", "test", null)
            app.flushLogs()
            val snapshot = async { app.createLogsSnapshot((root / "snapshot").toString()) }
            blocking.copyStarted.await()
            try {
                nse.log(Severity.Info, "contended", "test", null)
                assertTrue(withContext(Dispatchers.Default) { nse.flushLogsWithin(1_000) })
                assertFalse(fs.exists(root / "logs" / "wire_logs-nse.log"))
                assertFailsWith<IOException> { nse.deleteLogs() }
                assertFalse(withContext(Dispatchers.Default) { app.flushLogsWithin(25) })
                app.log(Severity.Info, "expired", "test", null)
                app.discardPendingLogs()
            } finally {
                blocking.releaseCopy()
            }
            snapshot.await()
            app.flushLogs()
            assertFalse("expired" in fs.read(root / "logs" / "wire_logs.log") { readUtf8() })
            assertTrue("\"discarded\":1" in fs.read(root / "logs" / "wire_logs.coverage.json") { readUtf8() })
            nse.log(Severity.Info, "after-copy", "test", null)
            nse.flushLogs()
            val coverage = fs.read(root / "logs" / "wire_logs-nse.coverage.json") { readUtf8() }
            assertTrue("\"storageFailed\":1" in coverage)
            assertTrue("\"written\":1" in coverage)
            assertTrue("after-copy" in fs.read(root / "logs" / "wire_logs-nse.log") { readUtf8() })
        }
    }

    @Test
    fun queueAndEntrySizesAreBoundedWithoutFormattingCopiesOnSubmission() = runTest {
        fixture { root ->
            val blocking = BlockingCopyFileSystem()
            val writer = writer(root, fileSystem = blocking)
            writer.log(Severity.Info, "seed", "test", null)
            writer.flushLogs()
            val snapshot = async { writer.createLogsSnapshot((root / "snapshot").toString()) }
            blocking.copyStarted.await()
            try {
                writer.log(Severity.Info, "x".repeat(33 * 1024), "test", null)
                repeat(100) { writer.log(Severity.Info, "queued-$it", "test", null) }
            } finally {
                blocking.releaseCopy()
            }
            snapshot.await()
            writer.flushLogs()
            val contents = fs.read(root / "logs" / "wire_logs.log") { readUtf8() }
            assertEquals(64, contents.lineSequence().count { "queued-" in it })
            val coverage = fs.read(root / "logs" / "wire_logs.coverage.json") { readUtf8() }
            assertTrue("\"rejected\":37" in coverage)
            assertTrue("\"written\":65" in coverage)
            assertFalse("queued-64" in contents)
            assertFalse("xxxxxxxxxx" in contents)
        }
    }

    @Test
    fun concurrentStreamsRotationSnapshotsAndDeletionKeepCompleteRecords() = runTest {
        fixture { root ->
            val app = writer(root, maxFileBytes = 1)
            val nse = writer(root, "nse", maxFileBytes = 1)
            val exporter = writer(root, "exporter")
            listOf(app, nse).mapIndexed { stream, writer ->
                async(Dispatchers.Default) {
                    repeat(50) { index ->
                        writer.log(Severity.Info, "stream-$stream-entry-$index", "concurrent", null)
                        assertTrue(writer.flushLogsWithin(2_000))
                    }
                }
            }.plus(async(Dispatchers.Default) {
                repeat(20) { index ->
                    try {
                        exporter.createLogsSnapshot((root / "snapshot-$index").toString())
                    } catch (_: IOException) {
                        // A writer owns the lock: exports can be retried without affecting receiving.
                    }
                    try {
                        exporter.deleteLogs()
                    } catch (_: IOException) {
                        // Deletion follows the same nonblocking cross-process coordination contract.
                    }
                }
            }).awaitAll()
            app.log(Severity.Info, "final-app", "concurrent", null)
            app.flushLogs()
            nse.log(Severity.Info, "final-nse", "concurrent", null)
            nse.flushLogs()
            exporter.createLogsSnapshot((root / "final-snapshot").toString())
            assertTrue(fs.exists(root / "final-snapshot" / "wire_logs.log"))
            assertTrue(fs.exists(root / "final-snapshot" / "wire_logs-nse.log"))
            fs.list(root).filter { fs.metadata(it).isDirectory }.forEach { directory ->
                fs.list(directory).filter { it.name.endsWith(".log") }.forEach { file ->
                    val contents = fs.read(file) { readUtf8() }
                    assertTrue(contents.endsWith("\n"))
                    assertTrue(contents.lineSequence().filter { it.isNotEmpty() }.all {
                        it.contains(" Info: (concurrent) ")
                    })
                }
            }
        }
    }

    private fun writer(
        root: Path,
        stream: String = "app",
        fileSystem: FileSystem = fs,
        maxFileBytes: Long = AppleDiagnosticLogWriter.DEFAULT_MAX_FILE_BYTES,
    ) = AppleDiagnosticLogWriter(root.toString(), stream, true, fileSystem, maxFileBytes)

    private suspend fun fixture(block: suspend (Path) -> Unit) {
        val root = (NSTemporaryDirectory() + "/diagnostic-logger-" + NSUUID().UUIDString).toPath()
        fs.createDirectories(root)
        try { block(root) } finally { fs.deleteRecursively(root) }
    }

    private class BlockingCopyFileSystem : ForwardingFileSystem(FileSystem.SYSTEM) {
        val copyStarted = CompletableDeferred<Unit>()
        private val condition = NSCondition()
        private var released = false

        override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
            check(!NSThread.isMainThread) { "Logger IO must not run on the main thread" }
            return path
        }

        override fun source(file: Path): Source {
            condition.lock()
            try {
                copyStarted.complete(Unit)
                while (!released) condition.wait()
            } finally { condition.unlock() }
            return super.source(file)
        }

        fun releaseCopy() {
            condition.lock()
            try {
                released = true
                condition.signal()
            } finally { condition.unlock() }
        }
    }
}
