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

package com.wire.kalium.logic.feature.user.screeenshotCensoring

import app.cash.turbine.test
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.feature.selfDeletingMessages.TeamSelfDeleteTimer
import com.wire.kalium.logic.feature.selfDeletingMessages.TeamSettingsSelfDeletionStatus
import com.wire.kalium.logic.feature.user.screenshotCensoring.ObserveScreenshotCensoringConfigResult
import com.wire.kalium.logic.feature.user.screenshotCensoring.ObserveScreenshotCensoringConfigUseCaseImpl
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ObserveScreenshotCensoringConfigUseCaseTest {

    private fun runTestWithParametersAndExpectedResult(
        observeScreenshotCensoringConfigResult: Either<StorageFailure, Boolean>,
        observeTeamSelfDeletingStatusResult: Either<StorageFailure, TeamSelfDeleteTimer>,
        expectedResult: ObserveScreenshotCensoringConfigResult
    ) = runTest {
        val (arrangement, observeScreenshotCensoringConfig) = Arrangement()
            .withObserveScreenshotCensoringConfigResult(observeScreenshotCensoringConfigResult)
            .withSuccessfulObserveTeamSelfDeletingStatusResult(observeTeamSelfDeletingStatusResult)
            .arrange()

        val result = observeScreenshotCensoringConfig()

        result.test {
            val item = awaitItem()
            assertTrue { item == expectedResult }

            verify(arrangement.userConfigRepository)
                .function(arrangement.userConfigRepository::observeScreenshotCensoringConfig)
                .with()
                .wasInvoked(once)

            verify(arrangement.userConfigRepository)
                .function(arrangement.userConfigRepository::observeTeamSettingsSelfDeletingStatus)
                .with()
                .wasInvoked(once)

            awaitComplete()
        }
    }

    @Test
    fun givenSSCensoringDisabledAndTeamSelfDeletingNotEnforced_whenInvoking_thenShouldReturnEnabledChosenByUser() =
        runTestWithParametersAndExpectedResult(
            observeScreenshotCensoringConfigResult = Either.Right(false),
            observeTeamSelfDeletingStatusResult = Either.Right(TeamSelfDeleteTimer.Enabled),
            expectedResult = ObserveScreenshotCensoringConfigResult.Disabled
        )

    @Test
    fun givenSSCensoringDisabledAndTeamSelfDeletingEnforced_whenInvoking_thenShouldReturnEnabledEnforcedByTeamSelfDeletingSettings() =
        runTestWithParametersAndExpectedResult(
            observeScreenshotCensoringConfigResult = Either.Right(false),
            observeTeamSelfDeletingStatusResult = Either.Right(TeamSelfDeleteTimer.Enforced(5L.toDuration(DurationUnit.MINUTES))),
            expectedResult = ObserveScreenshotCensoringConfigResult.Enabled.EnforcedByTeamSelfDeletingSettings
        )

    @Test
    fun givenSSCensoringDisabledAndTeamSelfDeletingFailure_whenInvoking_thenShouldReturnEnabledEnforcedByTeamSelfDeletingSettings() =
        runTestWithParametersAndExpectedResult(
            observeScreenshotCensoringConfigResult = Either.Right(false),
            observeTeamSelfDeletingStatusResult = Either.Left(StorageFailure.DataNotFound),
            expectedResult = ObserveScreenshotCensoringConfigResult.Enabled.EnforcedByTeamSelfDeletingSettings
        )

    @Test
    fun givenSSCensoringEnabledAndTeamSelfDeletingNotEnforced_whenInvoking_thenShouldReturnEnabledChosenByUser() =
        runTestWithParametersAndExpectedResult(
            observeScreenshotCensoringConfigResult = Either.Right(true),
            observeTeamSelfDeletingStatusResult = Either.Right(TeamSelfDeleteTimer.Enabled),
            expectedResult = ObserveScreenshotCensoringConfigResult.Enabled.ChosenByUser
        )

    @Test
    fun givenSSCensoringEnabledAndTeamSelfDeletingEnforced_whenInvoking_thenShouldReturnEnabledEnforcedByTeamSelfDeletingSettings() =
        runTestWithParametersAndExpectedResult(
            observeScreenshotCensoringConfigResult = Either.Right(true),
            observeTeamSelfDeletingStatusResult = Either.Right(TeamSelfDeleteTimer.Enforced(5L.toDuration(DurationUnit.MINUTES))),
            expectedResult = ObserveScreenshotCensoringConfigResult.Enabled.EnforcedByTeamSelfDeletingSettings
        )

    @Test
    fun givenSSCensoringEnabledAndTeamSelfDeletingFailure_whenInvoking_thenShouldReturnEnabledEnforcedByTeamSelfDeletingSettings() =
        runTestWithParametersAndExpectedResult(
            observeScreenshotCensoringConfigResult = Either.Right(true),
            observeTeamSelfDeletingStatusResult = Either.Left(StorageFailure.DataNotFound),
            expectedResult = ObserveScreenshotCensoringConfigResult.Enabled.EnforcedByTeamSelfDeletingSettings
        )

    @Test
    fun givenSSCensoringFailureAndTeamSelfDeletingNotEnforced_whenInvoking_thenShouldReturnEnabledChosenByUser() =
        runTestWithParametersAndExpectedResult(
            observeScreenshotCensoringConfigResult = Either.Left(StorageFailure.DataNotFound),
            observeTeamSelfDeletingStatusResult = Either.Right(TeamSelfDeleteTimer.Enabled),
            expectedResult = ObserveScreenshotCensoringConfigResult.Enabled.ChosenByUser
        )

    @Test
    fun givenSSCensoringFailureAndTeamSelfDeletingEnforced_whenInvoking_thenShouldReturnEnabledEnforcedByTeamSelfDeletingSettings() =
        runTestWithParametersAndExpectedResult(
            observeScreenshotCensoringConfigResult = Either.Left(StorageFailure.DataNotFound),
            observeTeamSelfDeletingStatusResult = Either.Right(TeamSelfDeleteTimer.Enforced(5L.toDuration(DurationUnit.MINUTES))),
            expectedResult = ObserveScreenshotCensoringConfigResult.Enabled.EnforcedByTeamSelfDeletingSettings
        )

    @Test
    fun givenSSCensoringFailureAndTeamSelfDeletingFailure_whenInvoking_thenShouldReturnEnabledEnforcedByTeamSelfDeletingSettings() =
        runTestWithParametersAndExpectedResult(
            observeScreenshotCensoringConfigResult = Either.Left(StorageFailure.DataNotFound),
            observeTeamSelfDeletingStatusResult = Either.Left(StorageFailure.DataNotFound),
            expectedResult = ObserveScreenshotCensoringConfigResult.Enabled.EnforcedByTeamSelfDeletingSettings
        )

    private class Arrangement {
        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        val observeScreenshotCensoringConfig = ObserveScreenshotCensoringConfigUseCaseImpl(userConfigRepository)

        fun withObserveScreenshotCensoringConfigResult(result: Either<StorageFailure, Boolean>) = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::observeScreenshotCensoringConfig)
                .whenInvoked()
                .thenReturn(flowOf(result))
        }

        fun withSuccessfulObserveTeamSelfDeletingStatusResult(result: Either<StorageFailure, TeamSelfDeleteTimer>) = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::observeTeamSettingsSelfDeletingStatus)
                .whenInvoked()
                .thenReturn(flowOf(result.map { TeamSettingsSelfDeletionStatus(false, it) }))
        }

        fun arrange() = this to observeScreenshotCensoringConfig
    }
}
