/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.share

import com.wire.kalium.logger.AppleDiagnosticLogWriter
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ShareLoggingTest {
    @Test
    fun liveStagesDoNotExposeUnstructuredMessages() {
        assertTrue(ShareSystemLogWriter.isPublicStage("Cells share stage=VERSION_STATE found=true draft=false"))
        assertTrue(ShareSystemLogWriter.isPublicStage("Share stage=SEND_COMPLETE success=false"))
        assertFalse(ShareSystemLogWriter.isPublicStage("Cells share stage=UPLOAD_START filename=private.txt"))
        assertFalse(ShareSystemLogWriter.isPublicStage("Cells share stage=UPLOAD_FAILED failure=https://example.com"))
        assertFalse(ShareSystemLogWriter.isPublicStage("REQUEST: Authorization=secret"))
        assertTrue(
            ShareSystemLogWriter.isPublicStage(
                "Cells share stage=PUBLISH_FAILED failure=ServerMiscommunication exception=InvalidRequestError status=403 cause=null"
            )
        )
    }

    @Test
    fun sharedSnapshotIncludesNativeShareFailureAndHonorsOptOut() = runBlocking {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "share-logging-${Uuid.random()}"
        val logger = ShareProcessLogger()
        val app = AppleDiagnosticLogWriter(root.toString(), streamName = "app")
        try {
            assertTrue(logger.configure(root.toString(), true))
            logger.recordNative("Share finished reason=UPLOAD_FAILED", true)
            assertTrue(logger.flush(1_000))
            assertTrue(logger.configure(root.toString(), false))
            logger.recordNative("disabled-entry", false)
            assertTrue(logger.flush(1_000))
            app.createLogsSnapshot((root / "snapshot").toString())
            val content = FileSystem.SYSTEM.read(root / "snapshot" / "wire_logs-share.log") { readUtf8() }
            assertTrue("Share finished reason=UPLOAD_FAILED" in content)
            assertFalse("disabled-entry" in content)
            assertFalse(logger.configure((root / "another-root").toString(), true))
        } finally {
            logger.configure(root.toString(), false)
            logger.flush(1_000)
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }
}
