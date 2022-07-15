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
    fun setLoggingLevel(level: KaliumLogLevel, logWriterList: List<LogWriter>? = null) {
        kaliumLogger = KaliumLogger(
            config = KaliumLogger.Config(
                severity = level,
                tag = "CoreLogic"
            ), logWriterList = logWriterList
        )

        callingLogger = KaliumLogger(
            config = KaliumLogger.Config(
                severity = level,
                tag = "Calling"
            ), logWriterList = logWriterList
        )

        NetworkLogger.setLoggingLevel(level = level, logWriterList = logWriterList)
        CryptographyLogger.setLoggingLevel(level = level, logWriterList = logWriterList)
        PersistenceLogger.setLoggingLevel(level = level, logWriterList = logWriterList)
    }
}
