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

package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.feature.user.readReceipts.PersistReadReceiptsStatusConfigUseCaseImpl
import com.wire.kalium.logic.feature.user.readReceipts.ReadReceiptStatusConfigResult
import com.wire.kalium.common.functional.Either
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PersistReadReceiptsStatusConfigUseCaseTest {

    @Test
    fun givenATrueValue_shouldCallSetEnabledReceipts() = runTest {
        val (arrangement, persistReadReceiptsStatusConfig) = Arrangement()
            .withSuccessfulCall()
            .arrange()

        val actual = persistReadReceiptsStatusConfig(true)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userPropertyRepository.setReadReceiptsEnabled()
        }
        assertTrue(actual is ReadReceiptStatusConfigResult.Success)
    }

    @Test
    fun givenAFalseValue_shouldCallDeleteReadReceipts() = runTest {
        val (arrangement, persistReadReceiptsStatusConfig) = Arrangement()
            .withSuccessfulCallToDelete()
            .arrange()

        val actual = persistReadReceiptsStatusConfig(false)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userPropertyRepository.deleteReadReceiptsProperty()
        }

        assertTrue(actual is ReadReceiptStatusConfigResult.Success)
    }

    @Test
    fun givenAValue_shouldAndFailsShouldReturnACoreFailureResult() = runTest {
        val (arrangement, persistReadReceiptsStatusConfig) = Arrangement()
            .withFailureToCallRepo()
            .arrange()

        val actual = persistReadReceiptsStatusConfig(true)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userPropertyRepository.setReadReceiptsEnabled()
        }

        assertTrue(actual is ReadReceiptStatusConfigResult.Failure)
    }

    private class Arrangement {

        val userPropertyRepository = mock<UserPropertyRepository>()

        val persistReadReceiptsStatusConfig = PersistReadReceiptsStatusConfigUseCaseImpl(userPropertyRepository)

        suspend fun withSuccessfulCall() = apply {
            everySuspend {
                userPropertyRepository.setReadReceiptsEnabled()
            } returns Either.Right(Unit)

            return this
        }

        suspend fun withSuccessfulCallToDelete() = apply {
            everySuspend {
                userPropertyRepository.deleteReadReceiptsProperty()
            } returns Either.Right(Unit)

            return this
        }

        suspend fun withFailureToCallRepo() = apply {
            everySuspend {
                userPropertyRepository.setReadReceiptsEnabled()
            } returns Either.Left(CoreFailure.Unknown(RuntimeException("Some error")))

            return this
        }

        fun arrange() = this to persistReadReceiptsStatusConfig
    }

}
