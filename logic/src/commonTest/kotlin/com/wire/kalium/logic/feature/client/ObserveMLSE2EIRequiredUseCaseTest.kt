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
import com.wire.kalium.logic.configuration.MLSE2EISetting
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.feature.user.MLSE2EIRequiredResult
import com.wire.kalium.logic.feature.user.ObserveMLSE2EIRequiredUseCase
import com.wire.kalium.logic.feature.user.ObserveMLSE2EIRequiredUseCaseImpl
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class ObserveMLSE2EIRequiredUseCaseTest {

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
            enablingDeadline = DateTimeUtil.currentInstant().plus(2.days)
        )
        val (_, useCase) = Arrangement()
            .withMLSE2EISetting(setting)
            .arrange()

        useCase().test {
            assertTrue { awaitItem() is MLSE2EIRequiredResult.WithGracePeriod }
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithDeadlineInPast_thenEmitResult() = runTest {
        val setting = MLS_E2EI_SETTING.copy(
            notifyUserAfter = DateTimeUtil.currentInstant(),
            enablingDeadline = DateTimeUtil.currentInstant()
        )
        val (_, useCase) = Arrangement()
            .withMLSE2EISetting(setting)
            .arrange()

        useCase().test {
            assertTrue { awaitItem() == MLSE2EIRequiredResult.NoGracePeriod }
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithNotifyDateInFuture_thenEmitResult() = runTest {
        val setting = MLS_E2EI_SETTING.copy(notifyUserAfter = DateTimeUtil.currentInstant().plus(1.days))
        val (_, useCase) = Arrangement()
            .withMLSE2EISetting(setting)
            .arrange()

        useCase().test {
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

    private class Arrangement {
        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        private var observeMLSEnabledUseCase: ObserveMLSE2EIRequiredUseCase = ObserveMLSE2EIRequiredUseCaseImpl(userConfigRepository)

        fun withMLSE2EISetting(setting: MLSE2EISetting) = apply {
            given(userConfigRepository)
                .function(userConfigRepository::observeIsMLSE2EISetting)
                .whenInvoked()
                .then { flowOf(Either.Right(setting)) }
        }

        fun arrange() = this to observeMLSEnabledUseCase
    }

    companion object {
        private val MLS_E2EI_SETTING = MLSE2EISetting(true, "some_url", null, null)
    }
}
