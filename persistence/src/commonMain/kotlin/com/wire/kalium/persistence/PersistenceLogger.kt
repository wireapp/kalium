package com.wire.kalium.persistence

import co.touchlab.kermit.LogWriter
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger

internal var kaliumLogger = KaliumLogger.disabled()

object PersistenceLogger {
    fun setLoggingLevel(level: KaliumLogLevel, logWriter: LogWriter) {
        kaliumLogger = KaliumLogger(
            config = KaliumLogger.Config(
                severity = level,
                tag = "Persistence"
            ), logWriter = logWriter
        )
    }
}
