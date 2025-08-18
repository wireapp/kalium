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
package com.wire.kalium.logic.feature.featureConfig

import app.cash.turbine.test
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.logic.configuration.UserConfigRepository
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveAppsEnabledConfigUseCaseTest {

    @Test
    fun givenUserConfigRepositoryFailureOrNotPresent_whenObservingAppsEnabledConfig_thenReturnFalse() = runTest {
        val (arrangement, observeAppsEnabledConfigUseCase) = Arrangement()
            .withObserveAppsEnabledResult(flowOf(StorageFailure.DataNotFound.left()))
            .arrange()

        val result = observeAppsEnabledConfigUseCase()

        result.test {
            val item = awaitItem()
            assertEquals(false, item)

            cancelAndIgnoreRemainingEvents()

            coVerify {
                arrangement.userConfigRepository.observeAppsEnabled()
            }.wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenUserConfigRepositorySuccess_whenObservingAppsEnabledConfig_thenReturnTrue() = runTest {
        val (arrangement, observeAppsEnabledConfigUseCase) = Arrangement()
            .withObserveAppsEnabledResult(flowOf(Either.Right(true)))
            .arrange()

        val result = observeAppsEnabledConfigUseCase()

        result.test {
            val item = awaitItem()
            assertEquals(true, item)

            cancelAndIgnoreRemainingEvents()

            coVerify {
                arrangement.userConfigRepository.observeAppsEnabled()
            }.wasInvoked(exactly = once)
        }
    }

    private class Arrangement {
        val userConfigRepository: UserConfigRepository = mock(UserConfigRepository::class)

        suspend fun withObserveAppsEnabledResult(result: Flow<Either<StorageFailure, Boolean>>) = apply {
            coEvery { userConfigRepository.observeAppsEnabled() } returns result
        }

        fun arrange(): Pair<Arrangement, ObserveAppsEnabledConfigUseCase> {
            return this to ObserveAppsEnabledConfigUseCaseImpl(userConfigRepository)
        }
    }

}
