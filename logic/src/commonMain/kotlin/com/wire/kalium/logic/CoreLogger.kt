package com.wire.kalium.logic

import co.touchlab.kermit.LogWriter
import com.wire.kalium.cryptography.CryptographyLogger
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.network.NetworkLogger
import com.wire.kalium.persistence.PersistenceLogger

internal var kaliumLogger = KaliumLogger.disabled()
internal var callingLogger = KaliumLogger.disabled()

object CoreLogger {
    fun setLoggingLevel(level: KaliumLogLevel, vararg logWriters: LogWriter = arrayOf()) {
        kaliumLogger = KaliumLogger(
            config = KaliumLogger.Config(
                severity = level,
                tag = "CoreLogic"
            ),
            logWriters = logWriters
        )

        callingLogger = KaliumLogger(
            config = KaliumLogger.Config(
                severity = level,
                tag = "Calling"
            ),
            logWriters = logWriters
        )

        NetworkLogger.setLoggingLevel(level = level, logWriters = logWriters)
        CryptographyLogger.setLoggingLevel(level = level, logWriters = logWriters)
        PersistenceLogger.setLoggingLevel(level = level, logWriters = logWriters)
    }
}
