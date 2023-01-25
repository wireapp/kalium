/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logger

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.platformLogWriter
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

/**
 * the logWriter is to create a custom writer other than the existing log writers from kermit to intercept the logs
 * in the android case we use it to write the logs on file
 *
 */
class KaliumLogger(private val config: Config, vararg logWriters: LogWriter = arrayOf()) {

    private var kermitLogger: KermitLogger

    init {
        kermitLogger = if (logWriters.isEmpty()) {
            KermitLogger(
                config = StaticConfig(
                    minSeverity = config.severityLevel, listOf(platformLogWriter())
                ),
                tag = config.tag
            )
        } else {
            KermitLogger(
                config = StaticConfig(
                    minSeverity = config.severityLevel, logWriters.asList()
                ),
                tag = config.tag
            )
        }
    }

    val severity = config.severity

    @Suppress("unused")
    fun withFeatureId(featureId: ApplicationFlow): KaliumLogger {
        val currentLogger = this
        currentLogger.kermitLogger = kermitLogger.withTag("featureId:${featureId.name.lowercase()}")
        return currentLogger
    }

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

    companion object {
        fun disabled(): KaliumLogger = KaliumLogger(
            config = Config.DISABLED
        )

        enum class ApplicationFlow {
            SYNC, EVENT_RECEIVER, CONVERSATIONS, CONNECTIONS, MESSAGES, SEARCH, SESSION, REGISTER, CLIENTS, CALLING, ASSETS, LOCAL_STORAGE
        }
    }
}
