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
package com.wire.kalium.logic.feature.e2ei

import org.junit.Test
import kotlin.test.assertEquals

class CertificateStatusCheckerTest {

    @Test
    fun givenOldTimestamp_whenCheckingTheStatus_thenReturnExpired() {
        val timestamp: Long = 1666681915000 // Tuesday, 25 October 2022 07:11:55
        val (_, certificateStatusChecker) = Arrangement()
            .arrange()

        val result = certificateStatusChecker.status(timestamp)

        assertEquals(CertificateStatus.EXPIRED, result)
    }

    @Test
    fun givenFutureTimestamp_whenCheckingTheStatus_thenReturnValid() {
        val timestamp = 4822355515000 // Sunday, 25 October 2122 07:11:55

        val (_, certificateStatusChecker) = Arrangement()
            .arrange()

        val result = certificateStatusChecker.status(timestamp)

        assertEquals(CertificateStatus.VALID, result)
    }

    class Arrangement {

        fun arrange() = this to CertificateStatusCheckerImpl()
    }
}