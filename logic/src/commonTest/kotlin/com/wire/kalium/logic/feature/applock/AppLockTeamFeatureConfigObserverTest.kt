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
package com.wire.kalium.logic.feature.applock

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.AppLockTeamConfig
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.AppLockModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class AppLockTeamFeatureConfigObserverTest {

    @Test
    fun givenRepositoryFailure_whenObservingAppLock_thenEmitNull() =
        runTest {

            val (arrangement, observer) = Arrangement()
                .withFailure()
                .arrange()

            val result = observer.invoke()

            verify {
                arrangement.userConfigRepository.observeAppLockConfig()
            }.wasInvoked(exactly = once)
            assertNull(result.first())
        }

    @Test
    fun givenRepositorySuccess_whenObservingAppLock_thenEmitAppLockConfigWithValueFromRepository() {
        runTest {
            val expectedAppLockValue = AppLockTeamConfig(
                appLockConfigModel.status.toBoolean(),
                appLockConfigModel.inactivityTimeoutSecs.seconds,
                isStatusChanged = false
            )
            val (arrangement, observer) = Arrangement()
                .withSuccess()
                .arrange()

            val result = observer.invoke()

            verify {
                arrangement.userConfigRepository.observeAppLockConfig()
            }.wasInvoked(exactly = once)
            assertEquals(expectedAppLockValue, result.first())
        }
    }

    private class Arrangement {

        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        fun withFailure(): Arrangement = apply {
            every {
                userConfigRepository.observeAppLockConfig()
            }.returns(flowOf(Either.Left(StorageFailure.DataNotFound)))
        }

        fun withSuccess(): Arrangement = apply {
            every {
                userConfigRepository.observeAppLockConfig()
            }.returns(flowOf(Either.Right(appLockTeamConfig)))
        }

        fun arrange() = this to AppLockTeamFeatureConfigObserverImpl(
            userConfigRepository = userConfigRepository
        )
    }

    companion object {
        val appLockConfigModel = AppLockModel(Status.ENABLED, 60)
        val appLockTeamConfig = AppLockTeamConfig(true, 60.seconds, false)
    }
}
