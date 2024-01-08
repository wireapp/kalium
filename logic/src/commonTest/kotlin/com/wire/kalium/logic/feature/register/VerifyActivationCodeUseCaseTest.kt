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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyActivationCodeUseCaseTest {
    @Mock
    private val registerAccountRepository = mock(classOf<RegisterAccountRepository>())

    private lateinit var verifyActivationCodeUseCase: VerifyActivationCodeUseCase

    @BeforeTest
    fun setup() {
        verifyActivationCodeUseCase = VerifyActivationCodeUseCase(registerAccountRepository)
    }

    @Test
    fun givenRepositoryCallIsSuccessful_thenSaucesIsPropagated() = runTest {
        val email = TEST_EMAIL
        val code = TEST_CODE
        given(registerAccountRepository)
            .coroutine { verifyActivationCode(email, code) }
            .then { Either.Right(Unit) }

        val actual = verifyActivationCodeUseCase(email, code)

        assertIs<VerifyActivationCodeResult.Success>(actual)

        verify(registerAccountRepository)
            .coroutine { verifyActivationCode(email, code) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallFailWithInvalidCode_thenInvalidCodeIsPropagated() = runTest {
        val email = TEST_EMAIL
        val code = TEST_CODE
        val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.invalidCode)

        given(registerAccountRepository)
            .coroutine { verifyActivationCode(email, code) }
            .then { Either.Left(expected) }

        val actual = verifyActivationCodeUseCase(email, code)

        assertIs<VerifyActivationCodeResult.Failure.InvalidCode>(actual)

        verify(registerAccountRepository)
            .coroutine { verifyActivationCode(email, code) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallFail_thenErrorIsPropagated() = runTest {
        val email = TEST_EMAIL
        val code = TEST_CODE
        val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)

        given(registerAccountRepository)
            .coroutine { verifyActivationCode(email, code) }
            .then { Either.Left(expected) }

        val actual = verifyActivationCodeUseCase(email, code)

        assertIs<VerifyActivationCodeResult.Failure.Generic>(actual)
        assertEquals(expected, actual.failure)

        verify(registerAccountRepository)
            .coroutine { verifyActivationCode(email, code) }
            .wasInvoked(exactly = once)
    }


    private companion object {
        const val TEST_EMAIL = """user@domain.com"""
        const val TEST_CODE = "123456"
    }

}

