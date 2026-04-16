/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

const val NOMAD_TRACE_TAG: String = "[NOMAD_TRACE]"
private const val DEFAULT_TEXT_PREVIEW_LENGTH = 80

fun KaliumLogger.nomadTrace(
    stage: String,
    fields: Map<String, Any?> = emptyMap(),
    level: KaliumLogLevel = KaliumLogLevel.DEBUG,
) {
    val fieldString = fields
        .filterValues { it != null }
        .entries
        .joinToString(separator = ", ") { (key, value) -> "$key=$value" }
    val message = if (fieldString.isBlank()) {
        "$NOMAD_TRACE_TAG $stage"
    } else {
        "$NOMAD_TRACE_TAG $stage: $fieldString"
    }
    when (level) {
        KaliumLogLevel.DEBUG -> d(message)
        KaliumLogLevel.INFO -> i(message)
        KaliumLogLevel.WARN -> w(message)
        KaliumLogLevel.ERROR -> e(message)
        KaliumLogLevel.VERBOSE -> v(message)
        KaliumLogLevel.DISABLED -> Unit
    }
}

fun nomadTraceTextPreview(text: String?, maxLength: Int = DEFAULT_TEXT_PREVIEW_LENGTH): String? =
    text
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.take(maxLength)
