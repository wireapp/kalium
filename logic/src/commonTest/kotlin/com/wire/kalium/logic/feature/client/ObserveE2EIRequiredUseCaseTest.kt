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
package com.wire.kalium.logic.feature.client

import app.cash.turbine.test
import com.wire.kalium.logic.configuration.E2EISettings
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.feature.user.E2EIRequiredResult
import com.wire.kalium.logic.feature.user.ObserveE2EIRequiredUseCase
import com.wire.kalium.logic.feature.user.ObserveE2EIRequiredUseCaseImpl
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveE2EIRequiredUseCaseTest {

    @Test
    fun givenSettingWithoutNotifyDate_thenNoEmitting() = runTest {
        val (_, useCase) = Arrangement()
            .withMLSE2EISetting(MLS_E2EI_SETTING)
            .withE2EINotificationTime(null)
            .withIsMLSSupported(true)
            .arrange()

        useCase().test {
            advanceUntilIdle()
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithNotifyDateInPast_thenEmitResult() = runTest {
        val setting = MLS_E2EI_SETTING.copy(
            gracePeriodEnd = DateTimeUtil.currentInstant().plus(2.days)
        )
        val (_, useCase) = Arrangement()
            .withMLSE2EISetting(setting)
            .withE2EINotificationTime(DateTimeUtil.currentInstant())
            .withIsMLSSupported(true)
            .arrange()

        useCase().test {
            assertTrue { awaitItem() is E2EIRequiredResult.WithGracePeriod }
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithDeadlineInPast_thenEmitResult() = runTest {
        val setting = MLS_E2EI_SETTING.copy(
            gracePeriodEnd = DateTimeUtil.currentInstant()
        )
        val (_, useCase) = Arrangement()
            .withMLSE2EISetting(setting)
            .withE2EINotificationTime(DateTimeUtil.currentInstant())
            .withIsMLSSupported(true)
            .arrange()

        useCase().test {
            assertTrue { awaitItem() == E2EIRequiredResult.NoGracePeriod.Create }
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithNotifyDateInFuture_thenEmitResultWithDelay() = runTest(TestKaliumDispatcher.io) {
        val delayDuration = 10.minutes
        val setting = MLS_E2EI_SETTING.copy(
            gracePeriodEnd = DateTimeUtil.currentInstant()
        )
        val (_, useCase) = Arrangement(TestKaliumDispatcher.io)
            .withMLSE2EISetting(setting)
            .withE2EINotificationTime(DateTimeUtil.currentInstant().plus(delayDuration))
            .withIsMLSSupported(true)
            .arrange()

        useCase().test {
            advanceTimeBy(delayDuration.minus(1.minutes).inWholeMilliseconds)
            expectNoEvents()

            advanceTimeBy(delayDuration.inWholeMilliseconds)
            assertTrue { awaitItem() == E2EIRequiredResult.NoGracePeriod.Create }
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithNotifyDateInPast_thenEmitResultWithoutDelay() = runTest(TestKaliumDispatcher.io) {
        val setting = MLS_E2EI_SETTING.copy(
            gracePeriodEnd = DateTimeUtil.currentInstant()
        )
        val (_, useCase) = Arrangement(TestKaliumDispatcher.io)
            .withMLSE2EISetting(setting)
            .withE2EINotificationTime(DateTimeUtil.currentInstant())
            .withIsMLSSupported(true)
            .arrange()

        useCase().test {
            advanceTimeBy(1000L)
            assertTrue { awaitItem() == E2EIRequiredResult.NoGracePeriod.Create }
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithoutDeadline_thenNoEmitting() = runTest {
        val (_, useCase) = Arrangement()
            .withMLSE2EISetting(MLS_E2EI_SETTING)
            .withE2EINotificationTime(null)
            .withIsMLSSupported(true)
            .arrange()

        useCase().test {
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithDisabledStatus_thenNoEmitting() = runTest {
        val setting = MLS_E2EI_SETTING.copy(isRequired = false)
        val (_, useCase) = Arrangement()
            .withMLSE2EISetting(setting)
            .withE2EINotificationTime(DateTimeUtil.currentInstant())
            .withIsMLSSupported(true)
            .arrange()

        useCase().test {
            assertTrue { awaitItem() == E2EIRequiredResult.NotRequired }
            awaitComplete()
        }
    }

    @Test
    fun givenMLSFeatureIsDisabled_thenNotRequiredIsEmitted() = runTest {
        val delayDuration = 10.minutes
        val setting = MLS_E2EI_SETTING.copy(
            gracePeriodEnd = DateTimeUtil.currentInstant()
        )
        val (arrangement, useCase) = Arrangement(TestKaliumDispatcher.io)
            .withMLSE2EISetting(setting)
            .withE2EINotificationTime(DateTimeUtil.currentInstant().plus(delayDuration))
            .withIsMLSSupported(false)
            .arrange()

        useCase().test {
            advanceUntilIdle()
            assertTrue { awaitItem() == E2EIRequiredResult.NotRequired }
            awaitComplete()
        }

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::observeE2EINotificationTime)
            .wasNotInvoked()
    }

    private class Arrangement(testDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()) {
        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        @Mock
        val featureSupport = mock(FeatureSupport::class)

        private var observeMLSEnabledUseCase: ObserveE2EIRequiredUseCase =
            ObserveE2EIRequiredUseCaseImpl(userConfigRepository, featureSupport, testDispatcher)

        fun withMLSE2EISetting(setting: E2EISettings) = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::observeE2EISettings)
                .whenInvoked()
                .then { flowOf(Either.Right(setting)) }
        }

        fun withE2EINotificationTime(instant: Instant?) = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::observeE2EINotificationTime)
                .whenInvoked()
                .then { flowOf(Either.Right(instant)) }
        }

        fun withIsMLSSupported(supported: Boolean) = apply {
            given(featureSupport)
                .invocation { featureSupport.isMLSSupported }
                .thenReturn(supported)
        }

        fun arrange() = this to observeMLSEnabledUseCase
    }

    companion object {
        private val MLS_E2EI_SETTING = E2EISettings(true, "some_url", null)
    }
}
