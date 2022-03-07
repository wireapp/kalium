package com.wire.kalium.cryptography

import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger

internal var kaliumLogger = KaliumLogger.disabledLogger()

object CryptographyLogger {
    fun setLoggingLevel(level: KaliumLogLevel) {
        kaliumLogger = KaliumLogger(
            config = KaliumLogger.Config(
                severity = level,
                tag = "Crypto"
            )
        )
    }
}
