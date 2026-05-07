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

import app.cash.turbine.test
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.common.functional.Either
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ObserveTypingIndicatorEnabledUseCaseTest {

    @Test
    fun givenATypingIndicatorState_whenInvokingObserveTypingIndicatorEnabled_thenShouldReturnsSuccessResult() = runTest {
        val (arrangement, observeTypingIndicatorEnabled) = Arrangement()
            .withSuccessfulState()
            .arrange()

        val result = observeTypingIndicatorEnabled()

        result.test {
            val item = awaitItem()
            assertTrue(item)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.userPropertyRepository.observeTypingIndicatorStatus()
            }

            awaitComplete()
        }
    }

    @Test
    fun givenATypingIndicatorState_whenFailureInvokingObserveTypingIndicatorEnabled_thenShouldReturnsTrueAndSuccessAsFallbackResult() =
        runTest {
            val (arrangement, observeTypingIndicatorEnabled) = Arrangement()
                .withFailureState()
                .arrange()

            val result = observeTypingIndicatorEnabled()

            result.test {
                val item = awaitItem()
                assertTrue(item)

                verifySuspend(VerifyMode.exactly(1)) {
                    arrangement.userPropertyRepository.observeTypingIndicatorStatus()
                }

                awaitComplete()
            }
        }

    private class Arrangement {

        val userPropertyRepository = mock<UserPropertyRepository>()

        val observeTypingIndicatorEnabled = ObserveTypingIndicatorEnabledUseCaseImpl(userPropertyRepository)

        suspend fun withSuccessfulState() = apply {
            everySuspend {
                userPropertyRepository.observeTypingIndicatorStatus()
            } returns flowOf(Either.Right(true))

            return this
        }

        suspend fun withFailureState() = apply {
            everySuspend {
                userPropertyRepository.observeTypingIndicatorStatus()
            } returns flowOf(Either.Left(StorageFailure.DataNotFound))

            return this
        }

        fun arrange() = this to observeTypingIndicatorEnabled
    }
}
