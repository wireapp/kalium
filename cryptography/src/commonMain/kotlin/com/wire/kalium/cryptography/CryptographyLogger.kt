package com.wire.kalium.cryptography

import co.touchlab.kermit.LogWriter
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger

internal var kaliumLogger = KaliumLogger.disabled()

object CryptographyLogger {
    fun setLoggingLevel(level: KaliumLogLevel, vararg logWriters: LogWriter = arrayOf()) {
        kaliumLogger = KaliumLogger(
            config = KaliumLogger.Config(
                severity = level,
                tag = "Crypto"
            ),
            logWriters = logWriters
        )
    }
}
