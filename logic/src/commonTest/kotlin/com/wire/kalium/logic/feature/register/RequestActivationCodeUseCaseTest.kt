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
class RequestActivationCodeUseCaseTest {
    @Mock
    private val registerAccountRepository = mock(classOf<RegisterAccountRepository>())

    private lateinit var requestActivationCodeUseCase: RequestActivationCodeUseCase

    @BeforeTest
    fun setup() {
        requestActivationCodeUseCase = RequestActivationCodeUseCase(registerAccountRepository)
    }

    @Test
    fun givenRepositoryCallIsSuccessful_thenSaucesIsPropagated() = runTest {
        val email = TEST_EMAIL
        given(registerAccountRepository)
            .coroutine { requestEmailActivationCode(email) }
            .then { Either.Right(Unit) }

        val actual = requestActivationCodeUseCase(email)

        assertIs<RequestActivationCodeResult.Success>(actual)

        verify(registerAccountRepository)
            .coroutine { requestEmailActivationCode(email) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallFail_thenErrorIsPropagated() = runTest {
        val email = TEST_EMAIL
        val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
        given(registerAccountRepository)
            .coroutine { requestEmailActivationCode(email) }
            .then { Either.Left(expected) }

        val actual = requestActivationCodeUseCase(email)

        assertIs<RequestActivationCodeResult.Failure.Generic>(actual)
        assertEquals(expected, actual.failure)

        verify(registerAccountRepository)
            .coroutine { requestEmailActivationCode(email) }
            .wasInvoked(exactly = once)
    }


    private companion object {
        const val TEST_EMAIL = """user@domain.com"""
    }

}
