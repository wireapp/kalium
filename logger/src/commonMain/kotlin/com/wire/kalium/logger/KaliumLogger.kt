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
import co.touchlab.kermit.LoggerConfig
import co.touchlab.kermit.MutableLoggerConfig
import co.touchlab.kermit.Severity
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

fun KaliumLogLevel.toMinSeverity(): Severity = when (this) {
    KaliumLogLevel.VERBOSE -> Severity.Verbose
    KaliumLogLevel.DEBUG -> Severity.Debug
    KaliumLogLevel.INFO -> Severity.Info
    KaliumLogLevel.WARN -> Severity.Warn
    KaliumLogLevel.ERROR -> Severity.Error
    KaliumLogLevel.DISABLED -> Severity.Assert
}

fun Severity.toKaliumLogLevel(): KaliumLogLevel = when (this) {
    Severity.Verbose -> KaliumLogLevel.VERBOSE
    Severity.Debug -> KaliumLogLevel.DEBUG
    Severity.Info -> KaliumLogLevel.INFO
    Severity.Warn -> KaliumLogLevel.WARN
    Severity.Error -> KaliumLogLevel.ERROR
    Severity.Assert -> KaliumLogLevel.DISABLED
}

/**
 * the logWriter is to create a custom writer other than the existing log writers from kermit to intercept the logs
 * in the android case we use it to write the logs on file
 *
 */
class KaliumLogger(
    private val config: Config = Config.DEFAULT,
    private val tag: Tag = Tag.Text("KaliumLogger")
) {

    constructor(
        config: Config = Config.DEFAULT,
        tag: String = "KaliumLogger"
    ) : this(config, Tag.Text(tag))
    private val kermitLogger: KermitLogger = KermitLogger(
        config = config.kermitConfig()
    )

    private fun tag(): String = when (tag) {
        is Tag.Text -> tag.text
        is Tag.UserClientText -> tag.data().let { "${tag.prefix}[${it.userId}|${it.clientId}]" }
    }

    fun logLevel(): KaliumLogLevel = config.logLevel()

    @Suppress("unused")
    fun withFeatureId(featureId: ApplicationFlow): KaliumLogger = KaliumLogger(
        config = config,
        tag = "featureId:${featureId.name.lowercase()}".let {
            when (tag) {
                is Tag.Text -> Tag.Text(it)
                is Tag.UserClientText -> Tag.UserClientText(it, tag.data)
            }
        }
    )

    fun withUserDeviceData(data: () -> UserClientData): KaliumLogger = KaliumLogger(
        config = config,
        tag = when (tag) {
            is Tag.Text -> Tag.UserClientText(tag.text, data)
            is Tag.UserClientText -> Tag.UserClientText(tag.prefix, data)
        }
    )

    @Suppress("unused")
    fun v(message: String, throwable: Throwable? = null, tag: String = this.tag()) =
        kermitLogger.v(message, throwable, tag)

    @Suppress("unused")
    fun d(message: String, throwable: Throwable? = null, tag: String = this.tag()) =
        kermitLogger.d(message, throwable, tag)

    @Suppress("unused")
    fun i(message: String, throwable: Throwable? = null, tag: String = this.tag()) =
        kermitLogger.i(message, throwable, tag)

    @Suppress("unused")
    fun w(message: String, throwable: Throwable? = null, tag: String = this.tag()) =
        kermitLogger.w(message, throwable, tag)

    @Suppress("unused")
    fun e(message: String, throwable: Throwable? = null, tag: String = this.tag()) =
        kermitLogger.e(message, throwable, tag)

    class Config(
        val initialLevel: KaliumLogLevel,
        val initialLogWriterList: List<LogWriter> = listOf(platformLogWriter())
    ) {
        private val mutableKermitConfig = object : MutableLoggerConfig {
            override var logWriterList: List<LogWriter> = initialLogWriterList
            override var minSeverity: Severity = initialLevel.toMinSeverity()
        }

        fun logLevel(): KaliumLogLevel = mutableKermitConfig.minSeverity.toKaliumLogLevel()

        fun kermitConfig(): LoggerConfig = mutableKermitConfig

        fun setLogLevel(level: KaliumLogLevel) {
            mutableKermitConfig.minSeverity = level.toMinSeverity()
        }

        fun setLogWriterList(logWriterList: List<LogWriter>) {
            mutableKermitConfig.logWriterList = logWriterList
        }

        companion object {
            val DEFAULT = disabled()
            fun disabled(): Config = Config(
                initialLevel = KaliumLogLevel.DISABLED,
                initialLogWriterList = listOf(platformLogWriter()),
            )
        }
    }

    sealed class Tag {
        data class Text(val text: String) : Tag()
        data class UserClientText(val prefix: String, val data: () -> UserClientData) : Tag()
    }

    data class UserClientData(val userId: String, val clientId: String) {
        fun addToTag(tag: String): String =
            if (!tag.matches(regex)) "$tag[${userId}|${clientId}]"
            else tag

        companion object {
            private val regex = Regex("^.*\\[.+\\|.+\\]\$")

            fun getFromTag(tag: String): UserClientData? =
                if (tag.matches(regex)) {
                    tag.substringAfterLast("[").substringBefore("]").split("|")
                        .let { data -> UserClientData(data[0], data[1]) }
                } else null
        }
    }

    companion object {
        fun disabled(): KaliumLogger = KaliumLogger(
            config = Config.disabled(),
            tag = "KaliumLogger"
        )

        enum class ApplicationFlow {
            SYNC, EVENT_RECEIVER, CONVERSATIONS, CONNECTIONS, MESSAGES, SEARCH, SESSION, REGISTER, CLIENTS, CALLING, ASSETS, LOCAL_STORAGE
        }
    }
}
