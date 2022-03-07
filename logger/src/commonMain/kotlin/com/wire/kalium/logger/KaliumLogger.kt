package com.wire.kalium.logger

import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.Logger as KermitLogger

/**
 * LoggerType in order from lowest to the highest severity level.
 */
enum class KaliumLogLevel {
    /**
     * A log level describing events showing step by step execution of your code that can be ignored during the standard operation,
     * but may be useful during extended debugging sessions.
     */
    VERBOSE,

    /**
     * A log level used for events considered to be useful during software debugging when more granular information is needed.
     */
    DEBUG,

    /**
     * An event happened, the event is purely informative and can be ignored during normal operations.
     */
    INFO,

    /**
     * Unexpected behavior happened inside the application,
     * but it is continuing its work and the key business features are operating as expected.
     */
    WARN,

    /**
     * One or more functionalities are not working, preventing some functionalities from working correctly.
     */
    ERROR,

    /**
     * Logger is Disabled.
     */
    DISABLED
}

class KaliumLogger(config: Config) {

    private val kermitLogger: KermitLogger

    init {
        kermitLogger = KermitLogger(
            config = StaticConfig(
                minSeverity = config.severityLevel
            ),
            tag = config.tag
        )
    }

    val severity = config.severity

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
        val severity: KaliumLogLevel,
        val tag: String
    ) {
        val severityLevel: Severity = when (severity) {
            KaliumLogLevel.VERBOSE -> Severity.Verbose
            KaliumLogLevel.DEBUG -> Severity.Debug
            KaliumLogLevel.INFO -> Severity.Info
            KaliumLogLevel.WARN -> Severity.Warn
            KaliumLogLevel.ERROR -> Severity.Error
            KaliumLogLevel.DISABLED -> Severity.Assert
        }

        companion object {
            val DISABLED = Config(
                severity = KaliumLogLevel.DISABLED,
                tag = "KaliumLogger"
            )
        }
    }
}
