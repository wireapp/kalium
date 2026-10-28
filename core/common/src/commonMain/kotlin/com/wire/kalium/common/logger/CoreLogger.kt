/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.common.logger

import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.util.serialization.toJsonElement

var kaliumLogger = KaliumLogger.disabled()
var callingLogger = KaliumLogger.disabled()

/**
 * Log a structured JSON message, in the following format:
 *
 * Example:
 * ```
 * leadingMessage: {map of key-value pairs represented as JSON}
 * ```
 * @param level the severity of the log message
 * @param leadingMessage the leading message useful for later grok parsing
 * @param jsonStringKeyValues the map of key-value pairs to be logged in a valid JSON format
 */
fun KaliumLogger.logStructuredJson(
    level: KaliumLogLevel,
    leadingMessage: String,
    jsonStringKeyValues: Map<String, Any?>
) {
    val logJson = jsonStringKeyValues.toJsonElement()
    val sanitizedLeadingMessage = if (leadingMessage.endsWith(":")) leadingMessage else "$leadingMessage:"
    val logMessage = "$sanitizedLeadingMessage $logJson"
    when (level) {
        KaliumLogLevel.DEBUG -> d(logMessage)
        KaliumLogLevel.INFO -> i(logMessage)
        KaliumLogLevel.WARN -> w(logMessage)
        KaliumLogLevel.ERROR -> e(logMessage)
        KaliumLogLevel.DISABLED,
        KaliumLogLevel.VERBOSE -> v(logMessage)
    }

}
