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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.AppLockTeamConfig
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.AppLockModel
import com.wire.kalium.logic.data.featureConfig.Status
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

internal class AppLockTeamFeatureConfigObserverTest {

    @Test
    fun givenRepositoryFailure_whenObservingAppLock_thenEmitNull() = runTest {
        val (arrangement, observer) = Arrangement()
            .withFailure()
            .withNomadServiceUrl(null)
            .arrange()

        assertNull(observer.invoke().first())

        verify(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.observeAppLockConfig()
        }
    }

    @Test
    fun givenRepositorySuccess_whenObservingAppLock_thenEmitAppLockConfigWithValueFromRepository() = runTest {
        val expectedAppLockValue = AppLockTeamConfig(
            appLockConfigModel.status.toBoolean(),
            appLockConfigModel.inactivityTimeoutSecs.seconds,
            isStatusChanged = false
        )
        val (arrangement, observer) = Arrangement()
            .withSuccess()
            .withNomadServiceUrl(null)
            .arrange()

        assertEquals(expectedAppLockValue, observer.invoke().first())

        verify(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.observeAppLockConfig()
        }
    }

    @Test
    fun givenSelfIsNomad_whenObservingAppLock_thenEmitNullRegardlessOfTeamConfig() = runTest {
        val (arrangement, observer) = Arrangement()
            .withSuccess()
            .withNomadServiceUrl("https://nomad.service.url")
            .arrange()

        assertNull(observer.invoke().first())

        verify(VerifyMode.exactly(0)) {
            arrangement.userConfigRepository.observeAppLockConfig()
        }
    }

    private class Arrangement {

        val userConfigRepository = mock<UserConfigRepository>(mode = MockMode.autoUnit)
        private var nomadServiceUrl: String? = null

        fun withFailure(): Arrangement = apply {
            every {
                userConfigRepository.observeAppLockConfig()
            } returns flowOf(Either.Left(StorageFailure.DataNotFound))
        }

        fun withSuccess(): Arrangement = apply {
            every {
                userConfigRepository.observeAppLockConfig()
            } returns flowOf(Either.Right(appLockTeamConfig))
        }

        fun withNomadServiceUrl(nomadServiceUrl: String?): Arrangement = apply {
            this.nomadServiceUrl = nomadServiceUrl
        }

        fun arrange() = this to AppLockTeamFeatureConfigObserverImpl(
            userConfigRepository = userConfigRepository,
            nomadServiceUrl = nomadServiceUrl,
        )
    }

    companion object {
        val appLockConfigModel = AppLockModel(Status.ENABLED, 60)
        val appLockTeamConfig = AppLockTeamConfig(true, 60.seconds, false)
    }
}
