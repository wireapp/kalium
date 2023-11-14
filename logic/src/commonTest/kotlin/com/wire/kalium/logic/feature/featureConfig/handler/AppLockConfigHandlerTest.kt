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
package com.wire.kalium.logic.feature.featureConfig.handler

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.AppLockTeamConfig
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.AppLockModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class AppLockConfigHandlerTest {

    @Test
    fun givenConfigRepositoryReturnsFailureWithStatusDisabled_whenHandlingTheEvent_ThenSetAppLockWithStatusChangedFalse() {
        val appLockModel = AppLockModel(Status.DISABLED, 20)
        val (arrangement, appLockConfigHandler) = Arrangement()
            .withUserConfigRepositoryFailure()
            .arrange()

        appLockConfigHandler.handle(appLockModel)

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::isTeamAppLockEnabled)
            .wasInvoked(exactly = once)

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setAppLockStatus)
            .with(
                eq(appLockModel.status.toBoolean()),
                eq(appLockModel.inactivityTimeoutSecs),
                eq(false)
            )
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenConfigRepositoryReturnsFailureWithStatusEnabled_whenHandlingTheEvent_ThenSetAppLockWithStatusChangedTrue() {
        val appLockModel = AppLockModel(Status.ENABLED, 20)
        val (arrangement, appLockConfigHandler) = Arrangement()
            .withUserConfigRepositoryFailure()
            .arrange()

        appLockConfigHandler.handle(appLockModel)

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::isTeamAppLockEnabled)
            .wasInvoked(exactly = once)

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setAppLockStatus)
            .with(
                eq(appLockModel.status.toBoolean()),
                eq(appLockModel.inactivityTimeoutSecs),
                eq(true)
            )
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNewStatusSameAsCurrent_whenHandlingTheEvent_ThenSetAppLockWithStatusChangedFalse() {
        val appLockModel = AppLockModel(Status.ENABLED, 44)
        val (arrangement, appLockConfigHandler) = Arrangement()
            .withAppLocked()
            .arrange()

        appLockConfigHandler.handle(appLockModel)

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::isTeamAppLockEnabled)
            .wasInvoked(exactly = once)

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setAppLockStatus)
            .with(
                eq(appLockModel.status.toBoolean()),
                eq(appLockModel.inactivityTimeoutSecs),
                eq(false)
            )
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStatusEnabledAndTimeoutDifferentFromCurrent_whenHandlingTheEvent_ThenSetAppLockWithStatusChangedTrue() {
        val appLockModel = AppLockModel(Status.ENABLED, 20)
        val (arrangement, appLockConfigHandler) = Arrangement()
            .withAppLocked()
            .arrange()

        appLockConfigHandler.handle(appLockModel)

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::isTeamAppLockEnabled)
            .wasInvoked(exactly = once)

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setAppLockStatus)
            .with(
                eq(appLockModel.status.toBoolean()),
                eq(appLockModel.inactivityTimeoutSecs),
                eq(true)
            )
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNewStatusDifferentThenCurrent_whenHandlingTheEvent_ThenSetAppLockWithStatusChangedTrue() {
        val appLockModel = AppLockModel(Status.ENABLED, 20)
        val (arrangement, appLockConfigHandler) = Arrangement()
            .withAppNotLocked()
            .arrange()

        appLockConfigHandler.handle(appLockModel)

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::isTeamAppLockEnabled)
            .wasInvoked(exactly = once)

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::setAppLockStatus)
            .with(
                eq(appLockModel.status.toBoolean()),
                eq(appLockModel.inactivityTimeoutSecs),
                eq(true)
            )
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val userConfigRepository: UserConfigRepository = mock(UserConfigRepository::class)

        fun arrange() = run {
            this@Arrangement to AppLockConfigHandler(
                userConfigRepository = userConfigRepository
            )
        }

        init {
            given(userConfigRepository)
                .function(userConfigRepository::setAppLockStatus)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withUserConfigRepositoryFailure() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::isTeamAppLockEnabled)
                .whenInvoked()
                .thenReturn(Either.Left(StorageFailure.DataNotFound))
        }

        fun withAppLocked() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::isTeamAppLockEnabled)
                .whenInvoked()
                .thenReturn(Either.Right(appLockTeamConfigEnabled))
        }

        fun withAppNotLocked() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::isTeamAppLockEnabled)
                .whenInvoked()
                .thenReturn(Either.Right(appLockTeamConfigDisabled))
        }
    }

    companion object {
        val appLockTeamConfigEnabled = AppLockTeamConfig(true, 44.seconds, false)
        val appLockTeamConfigDisabled = AppLockTeamConfig(false, 44.seconds, false)
    }
}
