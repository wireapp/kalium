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
    ASSERT,
    DISABLED
}

class KaliumLogger(initialConfig: Config) {

    private var kermitLogger: KermitLogger
    private var config: Config

    init {
        config = initialConfig
        kermitLogger = KermitLogger(
            config = StaticConfig(
                minSeverity = config.severityLevel()
            ),
            tag = config.tag
        )
    }

    fun isEnabled(): Boolean = config.severity != LoggerType.DISABLED

    fun v(message: String, throwable: Throwable? = null) =
        throwable?.let {
            kermitLogger.v(message, throwable)
        } ?: kermitLogger.v(message)

    fun d(message: String, throwable: Throwable? = null) =
        throwable?.let {
            kermitLogger.d(message, throwable)
        } ?: kermitLogger.d(message)

    fun i(message: String, throwable: Throwable? = null) =
        throwable?.let {
            kermitLogger.i(message, throwable)
        } ?: kermitLogger.i(message)

    fun w(message: String, throwable: Throwable? = null) =
        throwable?.let {
            kermitLogger.w(message, throwable)
        } ?: kermitLogger.w(message)

    fun e(message: String, throwable: Throwable? = null) =
        throwable?.let {
            kermitLogger.e(message, throwable)
        } ?: kermitLogger.e(message)

    fun a(message: String, throwable: Throwable? = null) =
        throwable?.let {
            kermitLogger.a(message, throwable)
        } ?: kermitLogger.a(message)


    class Config(
        val severity: LoggerType = LoggerType.DISABLED,
        val tag: String = "KaliumLogger"
    ) {
        fun severityLevel(): Severity = when (severity) {
            LoggerType.VERBOSE -> Severity.Verbose
            LoggerType.DEBUG -> Severity.Debug
            LoggerType.INFO -> Severity.Info
            LoggerType.WARN -> Severity.Warn
            LoggerType.ERROR -> Severity.Error
            LoggerType.ASSERT -> Severity.Assert
            LoggerType.DISABLED -> Severity.Error // TODO: Double check this severity level
        }

        companion object {
            val DISABLED = Config(
                severity = LoggerType.DISABLED,
                tag = "KaliumLogger"
            )
        }
    }
}
