/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.Status
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class AppsFeatureHandlerTest {

    @Test
    fun givenUserConfigRepositoryFailure_whenHandlingAppsFeature_ThenReturnFailure() = runTest {
        val (arrangement, appsFeatureHandler) = Arrangement()
            .withSetAppsEnabledFailure()
            .arrange()

        val result = appsFeatureHandler.handle(ConfigsStatusModel(Status.ENABLED))

        coVerify {
            arrangement.userConfigRepository.setAppsEnabled(eq(true))
        }.wasInvoked(exactly = once)

        assertTrue { result.isLeft() }
    }

    @Test
    fun givenUserConfigRepositorySuccess_whenHandlingAppsFeature_ThenReturnUnit() = runTest {
        val (arrangement, appsFeatureHandler) = Arrangement()
            .withSetAppsEnabledSuccess()
            .arrange()

        val result = appsFeatureHandler.handle(ConfigsStatusModel(Status.DISABLED))

        coVerify {
            arrangement.userConfigRepository.setAppsEnabled(eq(false))
        }.wasInvoked(exactly = once)

        assertTrue { result.isRight() }
    }

    private class Arrangement {
        val userConfigRepository: UserConfigRepository = mock(UserConfigRepository::class)

        fun arrange() = this to AppsFeatureHandler(userConfigRepository)

        suspend fun withSetAppsEnabledFailure() = apply {
            coEvery { userConfigRepository.setAppsEnabled(any()) } returns Either.Left(StorageFailure.DataNotFound)
        }

        suspend fun withSetAppsEnabledSuccess() = apply {
            coEvery { userConfigRepository.setAppsEnabled(any()) } returns Either.Right(Unit)
        }
    }
}

