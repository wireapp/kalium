package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.user.pushToken.PushTokenRequestBody
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

@ExperimentalCoroutinesApi
class PushTokenUseCaseTest {

    @Mock
    val loginRepository = mock(classOf<LoginRepository>())

    private lateinit var pushTokenUseCase: RegisterTokenUseCase

    @BeforeTest
    fun setup() {
        pushTokenUseCase = RegisterTokenUseCase(loginRepository)
    }


    @Test
    fun givenRepositoryCallIsSuccessful_thenSuccessIsReturned() = runTest {
        given(loginRepository)
            .coroutine { registerToken(pushTokenRequestBody) }
            .then { Either.Right(Unit) }

        val actual = pushTokenUseCase(
            senderId = "7239",
            clientId = "cliId", token = "7239", transport = "GCM"
        )

        assertIs<RegisterTokenResult.Success>(actual)

        verify(loginRepository)
            .coroutine { registerToken(pushTokenRequestBody) }
            .wasInvoked(exactly = once)
    }


    @Test
    fun givenRepositoryCallFailWithAppNotFound_thenInvalidCodeIsReturned() = runTest {
        val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.invalidCode)

        given(loginRepository)
            .coroutine { registerToken(pushTokenRequestBody) }
            .then { Either.Left(expected) }

        val actual = pushTokenUseCase(
            senderId = "7239",
            clientId = "cliId", token = "7239", transport = "GCM"
        )

        assertIs<RegisterTokenResult.Failure.AppNotFound>(actual)

        verify(loginRepository)
            .coroutine { registerToken(pushTokenRequestBody) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallFail_thenErrorIsReturned() = runTest {
        val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)

        given(loginRepository)
            .coroutine { registerToken(pushTokenRequestBody) }
            .then { Either.Left(expected) }

        val actual = pushTokenUseCase(
            senderId = "7239",
            clientId = "cliId", token = "7239", transport = "GCM"
        )

        assertIs<RegisterTokenResult.Failure.Generic>(actual)
        assertEquals(expected, actual.failure)

        verify(loginRepository)
            .coroutine { registerToken(pushTokenRequestBody) }
            .wasInvoked(exactly = once)
    }

    companion object {
        private val pushTokenRequestBody = PushTokenRequestBody(
            senderId = "7239",
            client = "cliId", token = "7239", transport = "GCM"
        )
    }
}
