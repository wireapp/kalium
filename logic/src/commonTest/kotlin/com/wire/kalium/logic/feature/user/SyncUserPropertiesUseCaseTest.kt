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

package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SyncUserPropertiesUseCaseTest {

    @Test
    fun givenPropertiesSyncSucceeds_whenInvoking_thenPropertiesAreSynced() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSyncPropertiesStatuses(Either.Right(Unit))
            .arrange()

        useCase()

        coVerify { arrangement.userPropertyRepository.syncPropertiesStatuses() }.wasInvoked(exactly = once)
    }

    @Test
    fun givenPropertiesSyncFails_whenInvoking_thenFailureIsReturned() = runTest {
        val failure = Either.Left(CoreFailure.Unknown(RuntimeException("failure")))
        val (arrangement, useCase) = Arrangement()
            .withSyncPropertiesStatuses(failure)
            .arrange()

        val result = useCase()

        coVerify { arrangement.userPropertyRepository.syncPropertiesStatuses() }.wasInvoked(exactly = once)
        assertTrue(result is Either.Left)
    }

    private class Arrangement {
        val userPropertyRepository = mock(UserPropertyRepository::class)

        private val useCase = SyncUserPropertiesUseCaseImpl(userPropertyRepository)

        suspend fun withSyncPropertiesStatuses(result: Either<CoreFailure, Unit>) = apply {
            coEvery { userPropertyRepository.syncPropertiesStatuses() }.returns(result)
        }

        fun arrange() = this to useCase
    }
}
