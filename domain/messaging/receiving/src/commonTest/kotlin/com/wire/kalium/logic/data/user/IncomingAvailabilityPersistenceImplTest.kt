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

package com.wire.kalium.logic.data.user

import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class IncomingAvailabilityPersistenceImplTest {

    @Test
    fun givenSenderAndStatus_whenUpdating_thenMappedIdAndStatusAreForwardedExactlyOnce() = runTest {
        val userDAO = mock<UserDAO>(mode = MockMode.autoUnit)
        val persistence = IncomingAvailabilityPersistenceImpl(userDAO)

        persistence.updateAvailabilityStatus(senderUserId, UserAvailabilityStatus.AWAY)

        verifySuspend(VerifyMode.exactly(1)) {
            userDAO.updateUserAvailabilityStatus(eq(senderUserIdEntity), eq(UserAvailabilityStatusEntity.AWAY))
        }
    }

    @Test
    fun givenDaoFailure_whenUpdating_thenSameExceptionEscapes() = runTest {
        val expected = IllegalStateException("availability update failed")
        val userDAO = mock<UserDAO>(mode = MockMode.autoUnit)
        everySuspend {
            userDAO.updateUserAvailabilityStatus(eq(senderUserIdEntity), eq(UserAvailabilityStatusEntity.BUSY))
        } throws expected
        val persistence = IncomingAvailabilityPersistenceImpl(userDAO)

        val actual = assertFailsWith<IllegalStateException> {
            persistence.updateAvailabilityStatus(senderUserId, UserAvailabilityStatus.BUSY)
        }

        assertSame(expected, actual)
    }

    @Test
    fun givenDaoCancellation_whenUpdating_thenSameCancellationEscapes() = runTest {
        val expected = CancellationException("availability update cancelled")
        val userDAO = mock<UserDAO>(mode = MockMode.autoUnit)
        everySuspend {
            userDAO.updateUserAvailabilityStatus(eq(senderUserIdEntity), eq(UserAvailabilityStatusEntity.AVAILABLE))
        } throws expected
        val persistence = IncomingAvailabilityPersistenceImpl(userDAO)

        val actual = assertFailsWith<CancellationException> {
            persistence.updateAvailabilityStatus(senderUserId, UserAvailabilityStatus.AVAILABLE)
        }

        assertSame(expected, actual)
    }

    private companion object {
        val senderUserId = UserId("sender-id", "wire.example")
        val senderUserIdEntity = UserIDEntity("sender-id", "wire.example")
    }
}
