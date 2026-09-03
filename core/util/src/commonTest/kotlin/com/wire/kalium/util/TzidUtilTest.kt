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
        val result = TzidUtil.plusDaysOrNull(instant = Instant.parse("2026-03-28T10:00:00+01:00"), days = 1, tzid = "Europe/Berlin")
        assertEquals(Instant.parse("2026-03-29T10:00:00+02:00"), result)
    }
}
