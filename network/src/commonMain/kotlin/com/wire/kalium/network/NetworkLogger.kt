package com.wire.kalium.network

import co.touchlab.kermit.LogWriter
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger

internal var kaliumLogger = KaliumLogger.disabled()

object NetworkLogger {
    fun setLoggingLevel(level: KaliumLogLevel, vararg logWriters: LogWriter = arrayOf()) {
        kaliumLogger = KaliumLogger(
            config = KaliumLogger.Config(
                severity = level,
                tag = "Network"
            ),
            logWriters = logWriters
        )
    }

    val isRequestLoggingEnabled: Boolean get() = kaliumLogger.severity in setOf(KaliumLogLevel.VERBOSE, KaliumLogLevel.DEBUG)
}
