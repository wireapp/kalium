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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.feature.user.screenshotCensoring.PersistScreenshotCensoringConfigResult
import com.wire.kalium.logic.feature.user.screenshotCensoring.PersistScreenshotCensoringConfigUseCaseImpl
import com.wire.kalium.common.functional.Either
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PersistScreenshotCensoringConfigUseCaseTest {

    @Test
    fun givenATrueValue_shouldCallSetScreenshotCensoringEnabled() = runTest {
        val (arrangement, persistScreenshotCensoringConfig) = Arrangement()
            .withSuccessfulCall()
            .arrange()
        val actual = persistScreenshotCensoringConfig(true)

        verifySuspend {
            arrangement.userPropertyRepository.setScreenshotCensoringEnabled()
        }
        assertTrue(actual is PersistScreenshotCensoringConfigResult.Success)
    }

    @Test
    fun givenAFalseValue_shouldCallDeleteScreenshotCensoringProperty() = runTest {
        val (arrangement, persistScreenshotCensoringConfig) = Arrangement()
            .withSuccessfulDeleteCall()
            .arrange()
        val actual = persistScreenshotCensoringConfig(false)

        verifySuspend {
            arrangement.userPropertyRepository.deleteScreenshotCensoringProperty()
        }

        assertTrue(actual is PersistScreenshotCensoringConfigResult.Success)
    }

    @Test
    fun givenAValue_shouldAndFailsShouldReturnACoreFailureResult() = runTest {
        val (arrangement, persistScreenshotCensoringConfig) = Arrangement()
            .withFailureToCallRepo()
            .arrange()
        val actual = persistScreenshotCensoringConfig(true)

        verifySuspend {
            arrangement.userPropertyRepository.setScreenshotCensoringEnabled()
        }

        assertTrue(actual is PersistScreenshotCensoringConfigResult.Failure)
    }

    @Test
    fun givenAFalseValueAndDeleteFails_shouldReturnACoreFailureResult() = runTest {
        val (arrangement, persistScreenshotCensoringConfig) = Arrangement()
            .withFailureToDeleteInRepo()
            .arrange()
        val actual = persistScreenshotCensoringConfig(false)

        verifySuspend {
            arrangement.userPropertyRepository.deleteScreenshotCensoringProperty()
        }

        assertTrue(actual is PersistScreenshotCensoringConfigResult.Failure)
    }

    private class Arrangement {

        val userPropertyRepository = mock<UserPropertyRepository>()

        val persistScreenshotCensoringConfig = PersistScreenshotCensoringConfigUseCaseImpl(userPropertyRepository)

        suspend fun withSuccessfulCall() = apply {
            everySuspend {
                userPropertyRepository.setScreenshotCensoringEnabled()
            } returns Either.Right(Unit)

            return this
        }

        suspend fun withSuccessfulDeleteCall() = apply {
            everySuspend {
                userPropertyRepository.deleteScreenshotCensoringProperty()
            } returns Either.Right(Unit)

            return this
        }

        suspend fun withFailureToCallRepo() = apply {
            everySuspend {
                userPropertyRepository.setScreenshotCensoringEnabled()
            } returns Either.Left(CoreFailure.Unknown(RuntimeException("Some error")))

            return this
        }

        suspend fun withFailureToDeleteInRepo() = apply {
            everySuspend {
                userPropertyRepository.deleteScreenshotCensoringProperty()
            } returns Either.Left(CoreFailure.Unknown(RuntimeException("Some error")))

            return this
        }

        fun arrange() = this to persistScreenshotCensoringConfig
    }
}
