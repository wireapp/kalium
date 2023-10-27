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
package com.wire.kalium.logic.feature.applock

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.AppLockConfigModel
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class AppLockTeamFeatureConfigObserverTest {

    @Test
    fun givenRepositoryFailure_whenObservingAppLock_thenEmitAppLockConfigWithDisabledStatus() =
        runTest {
            val expectedAppLockValue = AppLockConfig(
                false,
                AppLockTeamFeatureConfigObserverImpl.DEFAULT_TIMEOUT
            )
            val (arrangement, observer) = Arrangement()
                .withFailure()
                .arrange()

            val result = observer.invoke()

            verify(arrangement.userConfigRepository)
                .function(arrangement.userConfigRepository::observeAppLockStatus)
                .wasInvoked(exactly = once)
            assertEquals(expectedAppLockValue, result.first())
        }

    @Test
    fun givenRepositorySuccess_whenObservingAppLock_thenEmitAppLockConfigWithValueFromRepository() {
        runTest {
            val expectedAppLockValue = AppLockConfig(
                appLockConfigModel.enforceAppLock,
                appLockConfigModel.inactivityTimeoutSecs.seconds
            )
            val (arrangement, observer) = Arrangement()
                .withSuccess()
                .arrange()

            val result = observer.invoke()

            verify(arrangement.userConfigRepository)
                .function(arrangement.userConfigRepository::observeAppLockStatus)
                .wasInvoked(exactly = once)
            assertEquals(expectedAppLockValue, result.first())
        }
    }

    private class Arrangement {

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        fun withFailure(): Arrangement = apply {
            given(userConfigRepository)
                .function(userConfigRepository::observeAppLockStatus)
                .whenInvoked()
                .thenReturn(flowOf(Either.Left(StorageFailure.DataNotFound)))
        }

        fun withSuccess(): Arrangement = apply {
            given(userConfigRepository)
                .function(userConfigRepository::observeAppLockStatus)
                .whenInvoked()
                .thenReturn(flowOf(Either.Right(appLockConfigModel)))
        }

        fun arrange() = this to AppLockTeamFeatureConfigObserverImpl(userConfigRepository)
    }

    companion object {
        val appLockConfigModel = AppLockConfigModel(true, 60)
    }
}
