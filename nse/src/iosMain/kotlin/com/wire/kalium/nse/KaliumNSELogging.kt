/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.nse

import com.wire.kalium.common.logger.CoreLogger
import com.wire.kalium.logger.AppleDiagnosticLogWriter
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import kotlinx.coroutines.CancellationException
import platform.Foundation.NSLock

/** Process-lifetime logging. The host supplies its shared opt-in on each delivery. */
public object KaliumNSELogging {
    private val processLogger = NSEProcessLogger()

    /** Returns false if another root already owns this process's logger. */
    public fun configure(rootPath: String, enabled: Boolean): Boolean = processLogger.configure(rootPath, enabled)

    /** Best effort: completion must not wait indefinitely for another process's export. */
    @Throws(CancellationException::class)
    public suspend fun flush(timeoutMilliseconds: Long): Boolean = processLogger.flush(timeoutMilliseconds)
}

internal class NSEProcessLogger {
    private val lock = NSLock()
    private var installed: Installed? = null

    fun configure(rootPath: String, enabled: Boolean): Boolean = locked {
        val existing = installed
        if (existing != null && existing.rootPath != rootPath) return@locked false
        val current = existing ?: run {
            val writer = AppleDiagnosticLogWriter(rootPath, streamName = "nse")
            val config = KaliumLogger.Config(
                initialLevel = KaliumLogLevel.DISABLED,
                initialLogWriterList = listOf(co.touchlab.kermit.platformLogWriter(), writer),
            )
            Installed(rootPath, writer, config).also {
                CoreLogger.init(config)
                installed = it
            }
        }
        current.writer.updateLoggingEnabled(enabled)
        current.config.logLevel = if (enabled) KaliumLogLevel.INFO else KaliumLogLevel.DISABLED
        true
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
