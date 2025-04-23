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
package com.wire.kalium.logic.feature.user.typingIndicator

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PersistTypingIndicatorStatusConfigUseCaseTest {

    @Test
    fun givenATrueValue_shouldCallSetTypingIndicator() = runTest {
        val (arrangement, persistTypingIndicatorStatusConfig) = Arrangement()
            .withSuccessfulCall()
            .arrange()

        val actual = persistTypingIndicatorStatusConfig(true)

        coVerify {
            arrangement.userPropertyRepository.setTypingIndicatorEnabled()
        }.wasInvoked(once)
        assertTrue(actual is TypingIndicatorConfigResult.Success)
    }

    @Test
    fun givenAFalseValue_shouldCallRemoveTypingIndicator() = runTest {
        val (arrangement, persistTypingIndicatorStatusConfig) = Arrangement()
            .withSuccessfulCallToDelete()
            .arrange()

        val actual = persistTypingIndicatorStatusConfig(false)

        coVerify {
            arrangement.userPropertyRepository.removeTypingIndicatorProperty()
        }.wasInvoked(once)

        assertTrue(actual is TypingIndicatorConfigResult.Success)
    }

    @Test
    fun givenAValue_whenInvokedWithAFailureShouldReturnACoreFailureResult() = runTest {
        val (arrangement, persistTypingIndicatorStatusConfig) = Arrangement()
            .withFailureCallToRepo()
            .arrange()

        val actual = persistTypingIndicatorStatusConfig(true)

        coVerify {
            arrangement.userPropertyRepository.setTypingIndicatorEnabled()
        }.wasInvoked(once)

        assertTrue(actual is TypingIndicatorConfigResult.Failure)
    }

    private class Arrangement {
                val userPropertyRepository = mock(UserPropertyRepository::class)

        val persistTypingIndicatorStatusConfig = PersistTypingIndicatorStatusConfigUseCaseImpl(userPropertyRepository)

        suspend fun withSuccessfulCall() = apply {
            coEvery {
                userPropertyRepository.setTypingIndicatorEnabled()
            }.returns(Either.Right(Unit))

            return this
        }

        suspend fun withSuccessfulCallToDelete() = apply {
            coEvery {
                userPropertyRepository.removeTypingIndicatorProperty()
            }.returns(Either.Right(Unit))

            return this
        }

        suspend fun withFailureCallToRepo() = apply {
            coEvery {
                userPropertyRepository.setTypingIndicatorEnabled()
            }.returns(Either.Left(CoreFailure.Unknown(RuntimeException("Some error"))))

            return this
        }

        fun arrange() = this to persistTypingIndicatorStatusConfig
    }
}
