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
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
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
            .arrange()

        useCase().test {
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithNotifyDateInPast_thenEmitResult() = runTest {
        val setting = MLS_E2EI_SETTING.copy(
            notifyUserAfter = DateTimeUtil.currentInstant(),
            gracePeriodEnd = DateTimeUtil.currentInstant().plus(2.days)
        )
        val (_, useCase) = Arrangement()
            .withMLSE2EISetting(setting)
            .arrange()

        useCase().test {
            assertTrue { awaitItem() is E2EIRequiredResult.WithGracePeriod }
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithDeadlineInPast_thenEmitResult() = runTest {
        val setting = MLS_E2EI_SETTING.copy(
            notifyUserAfter = DateTimeUtil.currentInstant(),
            gracePeriodEnd = DateTimeUtil.currentInstant()
        )
        val (_, useCase) = Arrangement()
            .withMLSE2EISetting(setting)
            .arrange()

        useCase().test {
            assertTrue { awaitItem() == E2EIRequiredResult.NoGracePeriod }
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithNotifyDateInFuture_thenEmitResultWithDelay() = runTest(TestKaliumDispatcher.io) {
        val delayDuration = 10.minutes
        val setting = MLS_E2EI_SETTING.copy(
            notifyUserAfter = DateTimeUtil.currentInstant().plus(delayDuration),
            gracePeriodEnd = DateTimeUtil.currentInstant()
        )
        val (_, useCase) = Arrangement(TestKaliumDispatcher.io)
            .withMLSE2EISetting(setting)
            .arrange()

        useCase().test {
            advanceTimeBy(delayDuration.minus(1.minutes).inWholeMilliseconds)
            expectNoEvents()

            advanceTimeBy(delayDuration.inWholeMilliseconds)
            assertTrue { awaitItem() == E2EIRequiredResult.NoGracePeriod }
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithNotifyDateInPast_thenEmitResultWithoutDelay() = runTest(TestKaliumDispatcher.io) {
        val setting = MLS_E2EI_SETTING.copy(
            notifyUserAfter = DateTimeUtil.currentInstant(),
            gracePeriodEnd = DateTimeUtil.currentInstant()
        )
        val (_, useCase) = Arrangement(TestKaliumDispatcher.io)
            .withMLSE2EISetting(setting)
            .arrange()

        useCase().test {
            advanceTimeBy(1000L)
            assertTrue { awaitItem() == E2EIRequiredResult.NoGracePeriod }
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithoutDeadline_thenNoEmitting() = runTest {
        val (_, useCase) = Arrangement()
            .withMLSE2EISetting(MLS_E2EI_SETTING)
            .arrange()

        useCase().test {
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithDisabledStatus_thenNoEmitting() = runTest {
        val setting = MLS_E2EI_SETTING.copy(isRequired = false, notifyUserAfter = DateTimeUtil.currentInstant())
        val (_, useCase) = Arrangement()
            .withMLSE2EISetting(setting)
            .arrange()

        useCase().test {
            awaitComplete()
        }
    }

    private class Arrangement(testDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()) {
        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        private var observeMLSEnabledUseCase: ObserveE2EIRequiredUseCase =
            ObserveE2EIRequiredUseCaseImpl(userConfigRepository, testDispatcher)

        fun withMLSE2EISetting(setting: E2EISettings) = apply {
            given(userConfigRepository)
                .function(userConfigRepository::observeE2EISettings)
                .whenInvoked()
                .then { flowOf(Either.Right(setting)) }
        }

        fun arrange() = this to observeMLSEnabledUseCase
    }

    companion object {
        private val MLS_E2EI_SETTING = E2EISettings(true, "some_url", null, null)
    }
}
