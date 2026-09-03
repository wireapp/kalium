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

package com.wire.kalium.util

import com.wire.kalium.util.DateTimeUtil.MILLISECONDS_DIGITS
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.threeten.bp.Instant as ThreeTenInstant
import org.threeten.bp.ZoneId
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

actual open class PlatformDateTimeUtil actual constructor() {

    private val secondsDateTimeFormat = SimpleDateFormat(DateTimeUtil.simplePattern, Locale.getDefault())

    private val isoDateTimeFormatter = DateTimeFormatterBuilder().appendInstant(MILLISECONDS_DIGITS).toFormatter()

    /**
     * Parse [kotlinx.datetime.Instant] into date-time string in ISO-8601 format.
     * Regular `.toString()` can return different results on different platforms, for instance jvm uses [java.time.Instant]
     * which can ignore milliseconds when equal to 0 (.000) and change the result from YYYY-MM-DDTHH:mm:ss.SSSZ format
     * to YYYY-MM-DDTHH:mm:ssZ.
     * @param instant date-time as [kotlinx.datetime.Instant]
     * @return date in ISO-8601 format (YYYY-MM-DDTHH:mm:ss.SSSZ)
     */
    actual fun fromInstantToIsoDateTimeString(instant: Instant): String =
        isoDateTimeFormatter.format(instant.toJavaInstant())

    /**
     * Parse [kotlinx.datetime.Instant] into date-time string in simplified format with up to seconds precision.
     * @return date in simplified format (YYYY-MM-DD_HH:mm:ss)
     */
    actual fun fromInstantToSimpleDateTimeString(instant: Instant): String =
        secondsDateTimeFormat.format(System.currentTimeMillis())
}

// Temporary Android bridge: use ThreeTenBP's bundled IANA tzdb until kotlinx-datetime-zoneinfo publishes the KMP variants we need.
actual open class PlatformTzidUtil actual constructor() {
    actual fun isTimeZoneSupported(tzid: String): Boolean = toZoneIdOrNull(tzid) != null
    actual fun plusDaysOrNull(instant: Instant, days: Int, tzid: String): Instant? = toZoneIdOrNull(tzid)?.let { zoneId ->
        instant.toThreeTenInstant().atZone(zoneId).plusDays(days.toLong()).toInstant().toKotlinInstant()
    }
    private fun toZoneIdOrNull(tzid: String): ZoneId? = runCatching { ZoneId.of(tzid) }.getOrNull()
    private fun Instant.toThreeTenInstant(): ThreeTenInstant = ThreeTenInstant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())
    private fun ThreeTenInstant.toKotlinInstant(): Instant = Instant.fromEpochSeconds(epochSecond, nano.toLong())
}
