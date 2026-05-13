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
package com.wire.kalium.logic.feature.featureConfig.handler

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.AppLockTeamConfig
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.AppLockModel
import com.wire.kalium.logic.data.featureConfig.Status
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

internal class AppLockConfigHandlerTest {

    @Test
    fun givenConfigRepositoryReturnsFailureWithStatusDisabled_whenHandlingTheEvent_ThenSetAppLockWithStatusChangedFalse() = runTest {
        val appLockModel = AppLockModel(Status.DISABLED, 20)
        val (arrangement, appLockConfigHandler) = Arrangement()
            .withUserConfigRepositoryFailure()
            .arrange()

        appLockConfigHandler.handle(appLockModel)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.isTeamAppLockEnabled()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setAppLockStatus(
                eq(appLockModel.status.toBoolean()),
                eq(appLockModel.inactivityTimeoutSecs),
                eq(false)
            )
        }
    }

    @Test
    fun givenConfigRepositoryReturnsFailureWithStatusEnabled_whenHandlingTheEvent_ThenSetAppLockWithStatusChangedTrue() = runTest {
        val appLockModel = AppLockModel(Status.ENABLED, 20)
        val (arrangement, appLockConfigHandler) = Arrangement()
            .withUserConfigRepositoryFailure()
            .arrange()

        appLockConfigHandler.handle(appLockModel)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.isTeamAppLockEnabled()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setAppLockStatus(
                eq(appLockModel.status.toBoolean()),
                eq(appLockModel.inactivityTimeoutSecs),
                eq(true)
            )
        }
    }

    @Test
    fun givenNewStatusSameAsCurrent_whenHandlingTheEvent_ThenSetAppLockWithOldStatusChangedValue() = runTest {
        val appLockModel = AppLockModel(Status.ENABLED, 44)
        val (arrangement, appLockConfigHandler) = Arrangement()
            .withAppLocked()
            .arrange()

        appLockConfigHandler.handle(appLockModel)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.isTeamAppLockEnabled()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setAppLockStatus(
                eq(appLockModel.status.toBoolean()),
                eq(appLockModel.inactivityTimeoutSecs),
                eq(appLockTeamConfigEnabled.isStatusChanged)
            )
        }
    }

    @Test
    fun givenNewStatusDifferentThenCurrent_whenHandlingTheEvent_ThenSetAppLockWithStatusChangedTrue() = runTest {
        val appLockModel = AppLockModel(Status.ENABLED, 20)
        val (arrangement, appLockConfigHandler) = Arrangement()
            .withAppNotLocked()
            .arrange()

        appLockConfigHandler.handle(appLockModel)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.isTeamAppLockEnabled()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setAppLockStatus(
                eq(appLockModel.status.toBoolean()),
                eq(appLockModel.inactivityTimeoutSecs),
                eq(true)
            )
        }
    }

    private class Arrangement {

        val userConfigRepository: UserConfigRepository = mock<UserConfigRepository>(mode = MockMode.autoUnit)

        fun arrange() = run {
            this@Arrangement to AppLockConfigHandler(
                userConfigRepository = userConfigRepository
            )
        }

        init {
            runBlocking {
                everySuspend {
                    userConfigRepository.setAppLockStatus(any(), any(), any())
                } returns Either.Right(Unit)
            }
        }

        suspend fun withUserConfigRepositoryFailure() = apply {
            everySuspend {
                userConfigRepository.isTeamAppLockEnabled()
            } returns Either.Left(StorageFailure.DataNotFound)
        }

        suspend fun withAppLocked() = apply {
            everySuspend {
                userConfigRepository.isTeamAppLockEnabled()
            } returns Either.Right(appLockTeamConfigEnabled)
        }

        suspend fun withAppNotLocked() = apply {
            everySuspend {
                userConfigRepository.isTeamAppLockEnabled()
            } returns Either.Right(appLockTeamConfigDisabled)
        }
    }

    companion object {
        val appLockTeamConfigEnabled = AppLockTeamConfig(true, 44.seconds, false)
        val appLockTeamConfigDisabled = AppLockTeamConfig(false, 44.seconds, false)
    }
}
