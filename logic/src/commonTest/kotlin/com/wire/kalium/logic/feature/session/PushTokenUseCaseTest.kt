package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.notification.pushToken.PushTokenRequestBody
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
    val eventRepository = mock(classOf<EventRepository>())

    private lateinit var pushTokenUseCase: RegisterTokenUseCase

    @BeforeTest
    fun setup() {
        pushTokenUseCase = RegisterTokenUseCase(eventRepository)
    }


    @Test
    fun givenRepositoryCallIsSuccessful_thenSuccessIsReturned() = runTest {
        given(eventRepository)
            .coroutine { registerToken(pushTokenRequestBody) }
            .then { Either.Right(Unit) }

        val actual = pushTokenUseCase(
            senderId = "7239",
            clientId = "cliId", token = "7239", transport = "GCM"
        )

        assertIs<RegisterTokenResult.Success>(actual)

        verify(eventRepository)
            .coroutine { registerToken(pushTokenRequestBody) }
            .wasInvoked(exactly = once)
    }


    @Test
    fun givenRepositoryCallFailWithAppNotFound_thenInvalidCodeIsReturned() = runTest {
        val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.invalidCode)

        given(eventRepository)
            .coroutine { registerToken(pushTokenRequestBody) }
            .then { Either.Left(expected) }

        val actual = pushTokenUseCase(
            senderId = "7239",
            clientId = "cliId", token = "7239", transport = "GCM"
        )

        assertIs<RegisterTokenResult.Failure.AppNotFound>(actual)

        verify(eventRepository)
            .coroutine { registerToken(pushTokenRequestBody) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallFail_thenErrorIsReturned() = runTest {
        val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)

        given(eventRepository)
            .coroutine { registerToken(pushTokenRequestBody) }
            .then { Either.Left(expected) }

        val actual = pushTokenUseCase(
            senderId = "7239",
            clientId = "cliId", token = "7239", transport = "GCM"
        )

        assertIs<RegisterTokenResult.Failure.Generic>(actual)
        assertEquals(expected, actual.failure)

        verify(eventRepository)
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
