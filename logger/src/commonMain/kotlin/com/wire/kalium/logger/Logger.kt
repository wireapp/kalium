package com.wire.kalium.logger

import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.Logger as KermitLogger

enum class LoggerType {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    ASSERT
}

class Logger(
    tag: String,
    private val type: LoggerType
) {

    private var kermitLogger: KermitLogger

    init {
        kermitLogger = KermitLogger(
            config = StaticConfig(),
            tag = tag
        )
    }

    fun log(message: String, throwable: Throwable? = null) {
        val severity = when(type) {
            LoggerType.VERBOSE -> Severity.Verbose
            LoggerType.DEBUG -> Severity.Debug
            LoggerType.INFO -> Severity.Info
            LoggerType.WARN -> Severity.Warn
            LoggerType.ERROR -> Severity.Error
            LoggerType.ASSERT -> Severity.Assert
        }

        kermitLogger.log(
            severity = severity,
            throwable = throwable,
            message = message
        )
    }
}
