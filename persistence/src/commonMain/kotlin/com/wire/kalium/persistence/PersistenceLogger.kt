package com.wire.kalium.persistence

import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger

internal var kaliumLogger = KaliumLogger.disabled()

object PersistenceLogger {
    fun setLoggingLevel(level: KaliumLogLevel) {
        kaliumLogger = KaliumLogger(
            config = KaliumLogger.Config(
                severity = level,
                tag = "Persistence"
            )
        )
    }
}
