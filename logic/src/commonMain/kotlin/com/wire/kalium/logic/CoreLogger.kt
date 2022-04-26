package com.wire.kalium.logic

import com.wire.kalium.cryptography.CryptographyLogger
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.network.NetworkLogger

internal var kaliumLogger = KaliumLogger.disabled()
internal var callingLogger = KaliumLogger.disabled()

object CoreLogger {
    fun setLoggingLevel(level: KaliumLogLevel) {
        kaliumLogger = KaliumLogger(
            config = KaliumLogger.Config(
                severity = level,
                tag = "CoreLogic"
            )
        )

        callingLogger = KaliumLogger(
            config = KaliumLogger.Config(
                severity = level,
                tag = "calling"
            )
        )

        NetworkLogger.setLoggingLevel(level = level)
        CryptographyLogger.setLoggingLevel(level = level)
    }
}
