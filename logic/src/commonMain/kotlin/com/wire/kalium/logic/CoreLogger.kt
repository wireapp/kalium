package com.wire.kalium.logic

import co.touchlab.kermit.LogWriter
import com.wire.kalium.cryptography.CryptographyLogger
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.network.NetworkLogger

internal var kaliumLogger = KaliumLogger.disabled()
internal var callingLogger = KaliumLogger.disabled()

object CoreLogger {
    fun setLoggingLevel(level: KaliumLogLevel, logWriter: LogWriter? = null) {
        kaliumLogger = KaliumLogger(
            config = KaliumLogger.Config(
                severity = level,
                tag = "CoreLogic"
            ), logWriter = logWriter
        )

        callingLogger = KaliumLogger(
            config = KaliumLogger.Config(
                severity = level,
                tag = "calling"
            ), logWriter = logWriter
        )

        NetworkLogger.setLoggingLevel(level = level, logWriter = logWriter)
        CryptographyLogger.setLoggingLevel(level = level, logWriter = logWriter)
    }
}
