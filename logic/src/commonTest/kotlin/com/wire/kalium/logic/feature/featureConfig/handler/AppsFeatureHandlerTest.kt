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
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setAppsEnabled(eq(true))
        }

        assertTrue { result.isLeft() }
    }

    @Test
    fun givenUserConfigRepositorySuccess_whenHandlingAppsFeature_ThenReturnUnit() = runTest {
        val (arrangement, appsFeatureHandler) = Arrangement()
            .withSetAppsEnabledSuccess()
            .arrange()

        val result = appsFeatureHandler.handle(ConfigsStatusModel(Status.DISABLED))

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setAppsEnabled(eq(false))
        }

        assertTrue { result.isRight() }
    }

    private class Arrangement {
        val userConfigRepository: UserConfigRepository = mock<UserConfigRepository>(mode = MockMode.autoUnit)

        fun arrange() = this to AppsFeatureHandler(userConfigRepository)

        suspend fun withSetAppsEnabledFailure() = apply {
            everySuspend { userConfigRepository.setAppsEnabled(any()) } returns Either.Left(StorageFailure.DataNotFound)
        }

        suspend fun withSetAppsEnabledSuccess() = apply {
            everySuspend { userConfigRepository.setAppsEnabled(any()) } returns Either.Right(Unit)
        }
    }
}
