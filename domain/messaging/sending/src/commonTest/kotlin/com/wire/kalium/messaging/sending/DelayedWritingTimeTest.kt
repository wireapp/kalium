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

package com.wire.kalium.messaging.sending

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class DelayedWritingTimeTest {
    @Test
    fun writingTimestampIsSentOnlyAfterFiveMinutes() {
        val writtenAt = 1_700_000_000_000L
        assertNull(delayedWritingTime(writtenAt, Instant.fromEpochMilliseconds(writtenAt + 299_999)))
        assertNull(delayedWritingTime(writtenAt, Instant.fromEpochMilliseconds(writtenAt + 300_000)))
        assertEquals(writtenAt, delayedWritingTime(writtenAt, Instant.fromEpochMilliseconds(writtenAt + 300_001)))
        assertNull(delayedWritingTime(null, Instant.fromEpochMilliseconds(writtenAt + 300_001)))
    }
}
