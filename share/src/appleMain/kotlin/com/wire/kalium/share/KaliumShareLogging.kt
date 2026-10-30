/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.share

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.OSLogWriter
import co.touchlab.kermit.Severity
import com.wire.kalium.common.logger.CoreLogger
import com.wire.kalium.logger.AppleDiagnosticLogWriter
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import kotlinx.coroutines.CancellationException
import platform.Foundation.NSLock

/** Process-lifetime logging. The host supplies its shared opt-in on each delivery. */
public object KaliumShareLogging {
    private val processLogger = ShareProcessLogger()

    /** Returns false if another root already owns this process's logger. */
    public fun configure(rootPath: String, enabled: Boolean): Boolean = processLogger.configure(rootPath, enabled)

    /** Mirrors an already sanitized native message to the opt-in file only; Swift owns OSLog. */
    public fun recordNative(message: String, isError: Boolean) {
        processLogger.recordNative(message, isError)
    }

    /** Best effort: completion must not wait indefinitely for another process's export. */
    @Throws(CancellationException::class)
    public suspend fun flush(timeoutMilliseconds: Long): Boolean = processLogger.flush(timeoutMilliseconds)
}

internal class ShareProcessLogger {
    private val lock = NSLock()
    private var installed: Installed? = null

    fun configure(rootPath: String, enabled: Boolean): Boolean = locked {
        val existing = installed
        if (existing != null && existing.rootPath != rootPath) return@locked false
        val current = existing ?: run {
            val writer = AppleDiagnosticLogWriter(rootPath, streamName = "share")
            val config = KaliumLogger.Config(
                initialLevel = KaliumLogLevel.DISABLED,
                initialLogWriterList = listOf(ShareSystemLogWriter(), writer),
            )
            Installed(rootPath, writer, config).also {
                CoreLogger.init(config)
                installed = it
            }
        }
        current.writer.updateLoggingEnabled(enabled)
        current.config.logLevel = if (enabled) KaliumLogLevel.VERBOSE else KaliumLogLevel.DISABLED
        true
    }

    fun recordNative(message: String, isError: Boolean) {
        val writer = locked { installed?.writer } ?: return
        writer.log(if (isError) Severity.Error else Severity.Info, message, "Share", null)
    }

    suspend fun flush(timeoutMilliseconds: Long): Boolean {
        val writer = locked { installed?.writer } ?: return true
        return writer.flushLogsWithin(timeoutMilliseconds)
    }

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        return try { block() } finally { lock.unlock() }
    }

    private class Installed(
        val rootPath: String,
        val writer: AppleDiagnosticLogWriter,
        val config: KaliumLogger.Config,
    )
}

/** Only structured, content-free share stages are visible in live device logs. */
internal class ShareSystemLogWriter : LogWriter() {
    private val privateWriter = OSLogWriter(subsystem = "com.wire.prism.ios.share-ext", category = "Kalium")
    private val stageWriter = OSLogWriter(
        subsystem = "com.wire.prism.ios.share-ext",
        category = "Stages",
        publicLogging = true,
    )

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        if (isPublicStage(message)) {
            stageWriter.log(severity, message, "Share", null)
        } else {
            privateWriter.log(severity, message, tag, throwable)
        }
    }

    internal companion object {
        private val stagePattern = Regex(
            "^(Cells share|Share|Share handoff) stage=[A-Z_]+" +
                "( (files|index|attachments|drafts|items)=[0-9]+" +
                "| (cells|success|found|draft)=(true|false|null)" +
                "| (failure|type|result|exception|cause|status)=[A-Za-z0-9_-]+)*$"
        )

        fun isPublicStage(message: String): Boolean = stagePattern.matches(message)
    }
}
