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

package com.wire.kalium.logic.feature.user.screeenshotCensoring

import app.cash.turbine.test
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.message.TeamSelfDeleteTimer
import com.wire.kalium.logic.data.message.TeamSettingsSelfDeletionStatus
import com.wire.kalium.logic.feature.user.screenshotCensoring.ObserveScreenshotCensoringConfigResult
import com.wire.kalium.logic.feature.user.screenshotCensoring.ObserveScreenshotCensoringConfigUseCaseImpl
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
            assertEquals(expectedResult, item)

            coVerify {
                arrangement.userConfigRepository.observeScreenshotCensoringConfig()
            }.wasInvoked(once)

            coVerify {
                arrangement.userConfigRepository.observeTeamSettingsSelfDeletingStatus()
            }.wasInvoked(once)

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
    fun givenSSCensoringDisabledAndTeamSelfDeletingFailure_whenInvoking_thenShouldReturnDisabled() =
        runTestWithParametersAndExpectedResult(
            observeScreenshotCensoringConfigResult = Either.Right(false),
            observeTeamSelfDeletingStatusResult = Either.Left(StorageFailure.DataNotFound),
            expectedResult = ObserveScreenshotCensoringConfigResult.Disabled
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
    fun givenSSCensoringEnabledAndTeamSelfDeletingFailure_whenInvoking_thenShouldReturnEnabledChosenByUser() =
        runTestWithParametersAndExpectedResult(
            observeScreenshotCensoringConfigResult = Either.Right(true),
            observeTeamSelfDeletingStatusResult = Either.Left(StorageFailure.DataNotFound),
            expectedResult = ObserveScreenshotCensoringConfigResult.Enabled.ChosenByUser
        )

    @Test
    fun givenSSCensoringFailureAndTeamSelfDeletingNotEnforced_whenInvoking_thenShouldReturnDisabled() =
        runTestWithParametersAndExpectedResult(
            observeScreenshotCensoringConfigResult = Either.Left(StorageFailure.DataNotFound),
            observeTeamSelfDeletingStatusResult = Either.Right(TeamSelfDeleteTimer.Enabled),
            expectedResult = ObserveScreenshotCensoringConfigResult.Disabled
        )

    @Test
    fun givenSSCensoringFailureAndTeamSelfDeletingEnforced_whenInvoking_thenShouldReturnEnabledEnforcedByTeamSelfDeletingSettings() =
        runTestWithParametersAndExpectedResult(
            observeScreenshotCensoringConfigResult = Either.Left(StorageFailure.DataNotFound),
            observeTeamSelfDeletingStatusResult = Either.Right(TeamSelfDeleteTimer.Enforced(5L.toDuration(DurationUnit.MINUTES))),
            expectedResult = ObserveScreenshotCensoringConfigResult.Enabled.EnforcedByTeamSelfDeletingSettings
        )

    @Test
    fun givenSSCensoringFailureAndTeamSelfDeletingFailure_whenInvoking_thenShouldReturnDisabled() =
        runTestWithParametersAndExpectedResult(
            observeScreenshotCensoringConfigResult = Either.Left(StorageFailure.DataNotFound),
            observeTeamSelfDeletingStatusResult = Either.Left(StorageFailure.DataNotFound),
            expectedResult = ObserveScreenshotCensoringConfigResult.Disabled
        )

    private class Arrangement {

        val userConfigRepository = mock(UserConfigRepository::class)

        val observeScreenshotCensoringConfig = ObserveScreenshotCensoringConfigUseCaseImpl(userConfigRepository)

        suspend fun withObserveScreenshotCensoringConfigResult(result: Either<StorageFailure, Boolean>) = apply {
            coEvery {
                userConfigRepository.observeScreenshotCensoringConfig()
            }.returns(flowOf(result))
        }

        suspend fun withSuccessfulObserveTeamSelfDeletingStatusResult(result: Either<StorageFailure, TeamSelfDeleteTimer>) = apply {
            coEvery {
                userConfigRepository.observeTeamSettingsSelfDeletingStatus()
            }.returns(flowOf(result.map { TeamSettingsSelfDeletionStatus(false, it) }))
        }

        fun arrange() = this to observeScreenshotCensoringConfig
    }
}
