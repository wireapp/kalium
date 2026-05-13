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
package com.wire.kalium.logic.feature.auth

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.UserRepository
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

internal class PersistSelfUserEmailUseCaseTest {

    @Test
    fun givenEmail_whenPersistSelfUserEmailSucceeds_thenReturnSuccess() = runTest {
        val email = "user@email.com"
        val (arrangement, useCase) = Arrangement()
            .withSuccess()
            .arrange()

        val result = useCase(email)

        assertIs<PersistSelfUserEmailResult.Success>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.insertSelfIncompleteUserWithOnlyEmail(email)
        }
    }

    @Test
    fun givenEmail_whenPersistSelfUserEmailFails_thenReturnFailure() = runTest {
        val email = "user@email.com"
        val (arrangement, useCase) = Arrangement()
            .withFailure()
            .arrange()

        val result = useCase(email)

        assertIs<PersistSelfUserEmailResult.Failure>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.insertSelfIncompleteUserWithOnlyEmail(email)
        }
    }

    inner class Arrangement {

        val userRepository: UserRepository = mock(mode = MockMode.autoUnit)

        internal val useCase by lazy {
            PersistSelfUserEmailUseCaseImpl(userRepository = userRepository)
        }

        internal suspend fun withSuccess() = apply {
            everySuspend {
                userRepository.insertSelfIncompleteUserWithOnlyEmail(any())
            } returns Either.Right(Unit)
        }

        internal suspend fun withFailure() = apply {
            everySuspend {
                userRepository.insertSelfIncompleteUserWithOnlyEmail(any())
            } returns Either.Left(StorageFailure.Generic(RuntimeException("DB failed")))
        }

        internal fun arrange() = this to useCase
    }
}
