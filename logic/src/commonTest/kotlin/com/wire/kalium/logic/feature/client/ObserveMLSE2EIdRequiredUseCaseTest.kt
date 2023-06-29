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
import com.wire.kalium.logic.configuration.MLSE2EIdSetting
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.feature.user.ObserveMLSE2EIdRequiredUseCase
import com.wire.kalium.logic.feature.user.ObserveMLSE2EIdRequiredUseCaseImpl
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ObserveMLSE2EIdRequiredUseCaseTest {

    @Test
    fun givenSettingWithoutNotifyDate_thenNoEmitting() = runTest {
        val (_, useCase) = Arrangement()
            .withMLSE2EIdSetting(MLS_E2E_ID_SETTING)
            .arrange()

        useCase().test {
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithNotifyDateInPast_thenEmitResult() = runTest {
        val setting = MLS_E2E_ID_SETTING.copy(notifyUserAfter = DateTimeUtil.currentInstant())
        val (_, useCase) = Arrangement()
            .withMLSE2EIdSetting(setting)
            .arrange()

        useCase().test {
            assertEquals(setting, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithNotifyDateInFuture_thenEmitResult() = runTest {
        val setting = MLS_E2E_ID_SETTING.copy(notifyUserAfter = DateTimeUtil.currentInstant().plus(1L.toDuration(DurationUnit.DAYS)))
        val (_, useCase) = Arrangement()
            .withMLSE2EIdSetting(setting)
            .arrange()

        useCase().test {
            awaitComplete()
        }
    }

    @Test
    fun givenSettingWithDisabledStatus_thenNoEmitting() = runTest {
        val setting = MLS_E2E_ID_SETTING.copy(status = false, notifyUserAfter = DateTimeUtil.currentInstant())
        val (_, useCase) = Arrangement()
            .withMLSE2EIdSetting(setting)
            .arrange()

        useCase().test {
            awaitComplete()
        }
    }

    private class Arrangement {
        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        private var observeMLSEnabledUseCase: ObserveMLSE2EIdRequiredUseCase = ObserveMLSE2EIdRequiredUseCaseImpl(userConfigRepository)

        fun withMLSE2EIdSetting(setting: MLSE2EIdSetting) = apply {
            given(userConfigRepository)
                .function(userConfigRepository::observeIsMLSE2EIdSetting)
                .whenInvoked()
                .then { flowOf(setting) }
        }

        fun arrange() = this to observeMLSEnabledUseCase
    }

    companion object {
        private val MLS_E2E_ID_SETTING = MLSE2EIdSetting(true, "some_url", null, null)
    }
}
