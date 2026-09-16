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
package com.wire.kalium.util

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TzidUtilTest {

    @Test
    fun givenSupportedTimezone_whenCheckingSupport_thenReturnsTrue() {
        assertEquals(true, TzidUtil.isTimeZoneSupported("Europe/Berlin"))
    }

    @Test
    fun givenUnsupportedTimezone_whenCheckingSupport_thenReturnsFalse() {
        assertEquals(false, TzidUtil.isTimeZoneSupported("Unsupported/TZID"))
    }

    @Test
    fun givenUnsupportedTimezone_whenAddingDays_thenReturnsNull() {
        val result = TzidUtil.plusDaysOrNull(instant = Instant.parse("2026-03-28T09:00:00Z"), days = 1, tzid = "Unsupported/TZID")
        assertNull(result)
    }

    @Test
    fun givenTimezone_whenAddingDaysAcrossDaylightSavingChange_thenKeepsTheSameLocalTime() {

       // First case: DST change date for New York, 2026-03-08 02:00 New York time, for Berlin it's still not DST

        // 2026-03-07 in New York is the last day before DST starts, so it's still -5UTC, so 10:00-05:00
        val result1NY = TzidUtil.plusDaysOrNull(instant = Instant.parse("2026-03-07T10:00:00-05:00"), days = 1, tzid = "America/New_York")
        // 2026-03-08 at 02:00 in New York is when DST starts, changes to -4UTC, so to keep the same local time, it should be 10:00-04:00
        assertEquals(Instant.parse("2026-03-08T10:00:00-04:00"), result1NY)

        // 2026-03-07 in Berlin is before DST change, it's +1UTC, so 10:00+01:00
        val result1Berlin = TzidUtil.plusDaysOrNull(instant = Instant.parse("2026-03-08T10:00:00+01:00"), days = 1, tzid = "Europe/Berlin")
        // 2026-03-08 at 02:00 in Berlin is still before DST change, so it remains +1UTC, so 10:00+01:00
        assertEquals(Instant.parse("2026-03-09T10:00:00+01:00"), result1Berlin)

        // Second case: DST change date for Berlin, 2026-03-29 02:00 Berlin time, for New York it's already DST

        // 2026-03-28 in Berlin is the last day before DST starts, so it's still +1UTC, so 10:00+01:00
        val result2Berlin = TzidUtil.plusDaysOrNull(instant = Instant.parse("2026-03-28T10:00:00+01:00"), days = 1, tzid = "Europe/Berlin")
        // 2026-03-29 at 02:00 in Berlin is when DST starts, changes to +2UTC, so to keep the same local time, it should be 10:00+02:00
        assertEquals(Instant.parse("2026-03-29T10:00:00+02:00"), result2Berlin)

        // 2026-03-28 in New York is already in DST, it's -4UTC, so 10:00-04:00
        val result2NY = TzidUtil.plusDaysOrNull(instant = Instant.parse("2026-03-28T10:00:00-04:00"), days = 1, tzid = "America/New_York")
        // 2026-03-29 at 02:00 in New York is still in DST, so it remains -4UTC, so 10:00-04:00
        assertEquals(Instant.parse("2026-03-29T10:00:00-04:00"), result2NY)
    }
}
