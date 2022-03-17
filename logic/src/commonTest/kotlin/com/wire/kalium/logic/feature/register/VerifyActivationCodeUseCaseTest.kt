package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.ServerConfig
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
        val serverConfig = TEST_SERVER_CONFIG
        val code = TEST_CODE
        given(registerAccountRepository)
            .coroutine { verifyActivationCode(email, code, serverConfig.apiBaseUrl) }
            .then { Either.Right(Unit) }

        val actual = verifyActivationCodeUseCase(email, code, serverConfig)

        assertIs<VerifyActivationCodeResult.Success>(actual)

        verify(registerAccountRepository)
            .coroutine { verifyActivationCode(email, code, serverConfig.apiBaseUrl) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallFailWithInvalidCode_thenInvalidCodeIsPropagated() = runTest {
        val email = TEST_EMAIL
        val serverConfig = TEST_SERVER_CONFIG
        val code = TEST_CODE
        val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.invalidCode)

        given(registerAccountRepository)
            .coroutine { verifyActivationCode(email, code, serverConfig.apiBaseUrl) }
            .then { Either.Left(expected) }

        val actual = verifyActivationCodeUseCase(email, code, serverConfig)

        assertIs<VerifyActivationCodeResult.Failure.InvalidCode>(actual)

        verify(registerAccountRepository)
            .coroutine { verifyActivationCode(email, code, serverConfig.apiBaseUrl) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallFail_thenErrorIsPropagated() = runTest {
        val email = TEST_EMAIL
        val serverConfig = TEST_SERVER_CONFIG
        val code = TEST_CODE
        val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)

        given(registerAccountRepository)
            .coroutine { verifyActivationCode(email, code, serverConfig.apiBaseUrl) }
            .then { Either.Left(expected) }

        val actual = verifyActivationCodeUseCase(email, code, serverConfig)

        assertIs<VerifyActivationCodeResult.Failure.Generic>(actual)
        assertEquals(expected, actual.failure)

        verify(registerAccountRepository)
            .coroutine { verifyActivationCode(email, code, serverConfig.apiBaseUrl) }
            .wasInvoked(exactly = once)
    }


    private companion object {
        const val TEST_EMAIL = """user@domain.com"""
        const val TEST_CODE = "123456"
        val TEST_SERVER_CONFIG: ServerConfig = ServerConfig(
            apiBaseUrl = "apiBaseUrl.com",
            accountsBaseUrl = "accountsUrl.com",
            webSocketBaseUrl = "webSocketUrl.com",
            blackListUrl = "blackListUrl.com",
            teamsUrl = "teamsUrl.com",
            websiteUrl = "websiteUrl.com",
            title = "Test Title"
        )

    }

}

