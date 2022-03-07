package com.wire.kalium.network

import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger

internal var kaliumLogger = KaliumLogger.disabled()

object NetworkLogger {
    fun setLoggingLevel(level: KaliumLogLevel) {
        kaliumLogger = KaliumLogger(
            config = KaliumLogger.Config(
                severity = level,
                tag = "Network"
            )
        )
    }

    val isRequestLoggingEnabled: Boolean get() = kaliumLogger.severity in setOf(KaliumLogLevel.VERBOSE, KaliumLogLevel.DEBUG)
}
