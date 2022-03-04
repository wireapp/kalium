package com.wire.kalium.logger

import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.Logger as KermitLogger

// TODO: Add docs => https://sematext.com/blog/logging-levels/
enum class LoggerType {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    DISABLED
}

class KaliumLogger(config: Config) {

    private val kermitLogger: KermitLogger

    init {
        kermitLogger = KermitLogger(
            config = StaticConfig(
                minSeverity = config.severityLevel()
            ),
            tag = config.tag
        )
    }

    val severity = config.severity

    val isEnabled: Boolean = config.severity != LoggerType.DISABLED

    @Suppress("unused")
    fun v(message: String, throwable: Throwable? = null) =
        throwable?.let {
            kermitLogger.v(message, throwable)
        } ?: kermitLogger.v(message)

    @Suppress("unused")
    fun d(message: String, throwable: Throwable? = null) =
        throwable?.let {
            kermitLogger.d(message, throwable)
        } ?: kermitLogger.d(message)

    @Suppress("unused")
    fun i(message: String, throwable: Throwable? = null) =
        throwable?.let {
            kermitLogger.i(message, throwable)
        } ?: kermitLogger.i(message)

    @Suppress("unused")
    fun w(message: String, throwable: Throwable? = null) =
        throwable?.let {
            kermitLogger.w(message, throwable)
        } ?: kermitLogger.w(message)

    @Suppress("unused")
    fun e(message: String, throwable: Throwable? = null) =
        throwable?.let {
            kermitLogger.e(message, throwable)
        } ?: kermitLogger.e(message)

    class Config(
        val severity: LoggerType,
        val tag: String
    ) {
        fun severityLevel(): Severity = when (severity) {
            LoggerType.VERBOSE -> Severity.Verbose
            LoggerType.DEBUG -> Severity.Debug
            LoggerType.INFO -> Severity.Info
            LoggerType.WARN -> Severity.Warn
            LoggerType.ERROR -> Severity.Error
            LoggerType.DISABLED -> Severity.Assert
        }

        companion object {
            val DISABLED = Config(
                severity = LoggerType.DISABLED,
                tag = "KaliumLogger"
            )
        }
    }
}
