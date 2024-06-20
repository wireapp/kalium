/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
import com.wire.kalium.logger.KaliumLogger.Config
import com.wire.kalium.logger.KaliumLogger.Tag
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
 * Custom logger writer which uses multiplatform [KermitLogger] underneath to allow to customize log message or tag.
 * @param config the [Config] object which contains the configuration of the logger.
 * @param tag the [Tag] object which identifies the source of the log message. Can combine multiple data and turns it
 * into structured String used by the [KermitLogger] so that it can be parsed back again to the [Tag] object.
 * To know more how it behaves and what are the possibilities, take a look at the [Tag] sealed class and its subtypes.
 */
class KaliumLogger(
    private val config: Config = Config.DISABLED,
    private val tag: Tag = Tag.Text("KaliumLogger")
) {

    constructor(
        config: Config = Config.DISABLED,
        tag: String = "KaliumLogger"
    ) : this(config, Tag.Text(tag))

    private val kermitLogger: KermitLogger = KermitLogger(
        config = config.kermitConfig
    )

    private fun tag(): String = tag.tagString()

    val logLevel: KaliumLogLevel
        get() = config.logLevel

    /**
     * Creates a new logger with custom tag that replaces the old tag and allows to specify which specific app flow,
     * one of [ApplicationFlow], the logs sent by this logger relate to.
     * When the logger already contains [Tag.UserClientText] type of logs, then user-related tag data will still be included,
     * and this featureId tag part will be added as a prefix, to keep the standard pattern of the tag: "tag[userId|clientId]".
     * In this case it will become "featureId:featureName[userId|clientId]".
     * When current type of tag is [Tag.Text], then it will just replace it with the new one: "featureId:featureName".
     */
    fun withFeatureId(featureId: ApplicationFlow): KaliumLogger = withTextTag(
        featureId.name.lowercase()
    )

    /**
     * Creates a new logger with a custom tag that replaces the old tag.
     *
     * @param textTag The text tag to be added to the logger.
     * @return Returns a new instance of KaliumLogger with the updated tag.
     */
    fun withTextTag(textTag: String): KaliumLogger = KaliumLogger(
        config = config,
        tag = "featureId:$textTag".let {
            when (tag) {
                is Tag.Text -> Tag.Text(it)
                is Tag.UserClientText -> Tag.UserClientText(it, tag.data)
            }
        }
    )

    /**
     * Creates a new logger with custom tag that replaces the old tag and allows to add user-related data to the tag.
     * When the logger already contains [Tag.UserClientText] type of tag, then user-related tag data part will be replaced,
     * and if it contained already some text tag prefix part, then the same prefix will be also included in the new one,
     * to keep the standard pattern of the tag: "tag[userId|clientId]".
     */
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

    /**
     * Represents the configuration for the [KaliumLogger].
     *
     * @property initialLevel The initial log level for the logger.
     * @property initialLogWriterList The initial list of log writers for the logger.
     */
    data class Config(
        val initialLevel: KaliumLogLevel,
        val initialLogWriterList: List<LogWriter> = listOf(platformLogWriter()),
    ) {
        private val mutableKermitConfig = object : MutableLoggerConfig {
            override var logWriterList: List<LogWriter> = initialLogWriterList
            override var minSeverity: Severity = initialLevel.toMinSeverity()
        }

        var logLevel: KaliumLogLevel
            get() = mutableKermitConfig.minSeverity.toKaliumLogLevel()
            set(value) {
                mutableKermitConfig.minSeverity = value.toMinSeverity()
            }

        val kermitConfig: LoggerConfig
            get() = mutableKermitConfig

        companion object {
            val DISABLED = Config(
                initialLevel = KaliumLogLevel.DISABLED,
                initialLogWriterList = listOf(platformLogWriter()),
            )
        }
    }

    /**
     * Defined types of tags that can be provided to the [KaliumLogger] as a String text.
     */
    sealed class Tag {
        abstract fun tagString(): String

        /**
         * Simple String text tag.
         */
        data class Text(val text: String) : Tag() {
            override fun tagString(): String = text
        }

        /**
         * User-related data tag. Contains String text prefix and [UserClientData] (userId and clientId).
         * It will be added to the tag in the standard pattern: "tag[userId|clientId]",
         * so it can be combined with a [Tag.Text] type by adding the tag text as a prefix in this one.
         */
        data class UserClientText(val prefix: String, val data: () -> UserClientData) : Tag() {
            override fun tagString(): String = data().let { "$prefix[${it.userId}|${it.clientId}]" }
        }
    }

    data class LogAttributes(
        val userClientData: UserClientData?,
        val textTag: String
    ) {
        companion object {
            private val regex = Regex("^.*\\[.+\\|.*\\]\$")

            /**
             * Parses the user-related data from the String tag in the standard pattern: "tag[userId|clientId]".
             * Returns null if the tag doesn't match the pattern, which means it does not contain user-related data.
             */
            @Suppress("unused")
            fun getInfoFromTagString(tag: String): LogAttributes =
                if (tag.matches(regex)) {
                    val prefix = tag.substringBefore("[")
                    val userClientData = tag.substringAfterLast("[").substringBefore("]").split("|")
                        .let { data -> UserClientData(data[0], data[1]) }
                    LogAttributes(userClientData, prefix)
                } else {
                    LogAttributes(null, tag)
                }
        }
    }

    data class UserClientData(val userId: String, val clientId: String?)

    companion object {
        fun disabled(): KaliumLogger = KaliumLogger(
            config = Config.DISABLED,
            tag = "KaliumLogger"
        )

        enum class ApplicationFlow {
            SYNC, EVENT_RECEIVER, CONVERSATIONS, CONNECTIONS, MESSAGES, SEARCH, SESSION, REGISTER, CLIENTS, CALLING, ASSETS, LOCAL_STORAGE
        }
    }
}
