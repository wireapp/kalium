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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.feature.user.screenshotCensoring.PersistScreenshotCensoringConfigResult
import com.wire.kalium.logic.feature.user.screenshotCensoring.PersistScreenshotCensoringConfigUseCaseImpl
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PersistScreenshotCensoringConfigUseCaseTest {

    @Test
    fun givenATrueValue_shouldCallSetScreenshotCensoringConfigWithTrue() = runTest {
        val (arrangement, persistScreenshotCensoringConfig) = Arrangement()
            .withSuccessfulCall()
            .arrange()
        val value = true
        val actual = persistScreenshotCensoringConfig(value)

        coVerify {
            arrangement.userConfigRepository.setScreenshotCensoringConfig(eq(value))
        }.wasInvoked(once)
        assertTrue(actual is PersistScreenshotCensoringConfigResult.Success)
    }

    @Test
    fun givenAFalseValue_shouldCallSetScreenshotCensoringConfigWithFalse() = runTest {
        val (arrangement, persistScreenshotCensoringConfig) = Arrangement()
            .withSuccessfulCall()
            .arrange()
        val value = false
        val actual = persistScreenshotCensoringConfig(value)

        coVerify {
            arrangement.userConfigRepository.setScreenshotCensoringConfig(eq(value))
        }.wasInvoked(once)

        assertTrue(actual is PersistScreenshotCensoringConfigResult.Success)
    }

    @Test
    fun givenAValue_shouldAndFailsShouldReturnACoreFailureResult() = runTest {
        val (arrangement, persistScreenshotCensoringConfig) = Arrangement()
            .withFailureToCallRepo()
            .arrange()
        val value = true
        val actual = persistScreenshotCensoringConfig(value)

        coVerify {
            arrangement.userConfigRepository.setScreenshotCensoringConfig(any())
        }.wasInvoked(once)

        assertTrue(actual is PersistScreenshotCensoringConfigResult.Failure)
    }

    private class Arrangement {
        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        val persistScreenshotCensoringConfig = PersistScreenshotCensoringConfigUseCaseImpl(userConfigRepository)

        suspend fun withSuccessfulCall() = apply {
            coEvery {
                userConfigRepository.setScreenshotCensoringConfig(any())
            }.returns(Either.Right(Unit))

            return this
        }

        suspend fun withFailureToCallRepo() = apply {
            coEvery {
                userConfigRepository.setScreenshotCensoringConfig(any())
            }.returns(Either.Left(StorageFailure.Generic(RuntimeException("Some error"))))

            return this
        }

        fun arrange() = this to persistScreenshotCensoringConfig
    }
}
