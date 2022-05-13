package com.wire.kalium.cryptography

import co.touchlab.kermit.LogWriter
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger

internal var kaliumLogger = KaliumLogger.disabled()

object CryptographyLogger {
    fun setLoggingLevel(level: KaliumLogLevel, logWriter: LogWriter? = null) {
        kaliumLogger = KaliumLogger(
            config = KaliumLogger.Config(
                severity = level,
                tag = "Crypto"
            ), logWriter = logWriter
        )
    }
}
