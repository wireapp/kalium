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

package com.wire.kalium.logic.feature.register

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RequestActivationCodeUseCaseTest {
        private val registerAccountRepository = mock(RegisterAccountRepository::class)

    private lateinit var requestActivationCodeUseCase: RequestActivationCodeUseCase

    @BeforeTest
    fun setup() {
        requestActivationCodeUseCase = RequestActivationCodeUseCase(registerAccountRepository)
    }

    @Test
    fun givenRepositoryCallIsSuccessful_thenSaucesIsPropagated() = runTest {
        val email = TEST_EMAIL
        coEvery {
            registerAccountRepository.requestEmailActivationCode(email)
        }.returns(Either.Right(Unit))

        val actual = requestActivationCodeUseCase(email)

        assertIs<RequestActivationCodeResult.Success>(actual)

        coVerify {
            registerAccountRepository.requestEmailActivationCode(email)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallFail_thenErrorIsPropagated() = runTest {
        val email = TEST_EMAIL
        val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
        coEvery {
            registerAccountRepository.requestEmailActivationCode(email)
        }.returns(Either.Left(expected))

        val actual = requestActivationCodeUseCase(email)

        assertIs<RequestActivationCodeResult.Failure.Generic>(actual)
        assertEquals(expected, actual.failure)

        coVerify {
            registerAccountRepository.requestEmailActivationCode(email)
        }.wasInvoked(exactly = once)
    }

    private companion object {
        const val TEST_EMAIL = """user@domain.com"""
    }

}
