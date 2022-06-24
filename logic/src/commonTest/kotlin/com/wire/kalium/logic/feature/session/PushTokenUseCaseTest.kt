package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.notification.NotificationToken
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.user.pushToken.PushTokenBody
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
class PushTokenUseCaseTest {

    @Mock
    val clientRemoteRepository = mock(classOf<ClientRepository>())

    @Mock
    private val notificationTokenRepository = mock(classOf<NotificationTokenRepository>())

    private lateinit var pushTokenUseCase: RegisterTokenUseCase

    @BeforeTest
    fun setup() {
        pushTokenUseCase = RegisterTokenUseCaseImpl(clientRemoteRepository, notificationTokenRepository)
        given(notificationTokenRepository).function(notificationTokenRepository::getNotificationToken).whenInvoked().thenReturn(
            Either.Right(NotificationToken(token = MOCK_TOKEN, transport = MOCK_TRANSPORT))
        )
    }


    @Test
    fun givenRepositoryCallIsSuccessful_thenSuccessIsReturned() = runTest {
        given(clientRemoteRepository)
            .coroutine { registerToken(pushTokenRequestBody) }
            .then { Either.Right(Unit) }

        val actual = pushTokenUseCase(
            senderId = "7239",
            clientId = ClientId("cliId")
        )

        assertIs<RegisterTokenResult.Success>(actual)

        verify(clientRemoteRepository)
            .coroutine { registerToken(pushTokenRequestBody) }
            .wasInvoked(exactly = once)
    }


    @Test
    fun givenRepositoryCallFailWithAppNotFound_thenInvalidCodeIsReturned() = runTest {
        val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.invalidCode)

        given(clientRemoteRepository)
            .coroutine { registerToken(pushTokenRequestBody) }
            .then { Either.Left(expected) }

        val actual = pushTokenUseCase(
            senderId = "7239",
            clientId = ClientId("cliId")
        )

        assertIs<RegisterTokenResult.Failure.AppNotFound>(actual)

        verify(clientRemoteRepository)
            .coroutine { registerToken(pushTokenRequestBody) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallFail_thenErrorIsReturned() = runTest {
        val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)

        given(clientRemoteRepository)
            .coroutine { registerToken(pushTokenRequestBody) }
            .then { Either.Left(expected) }

        val actual = pushTokenUseCase(
            senderId = "7239",
            clientId = ClientId("cliId")
        )

        assertIs<RegisterTokenResult.Failure.Generic>(actual)
        assertEquals(expected, actual.failure)

        verify(clientRemoteRepository)
            .coroutine { registerToken(pushTokenRequestBody) }
            .wasInvoked(exactly = once)
    }

    companion object {
        private const val MOCK_TOKEN = "7239"
        private const val MOCK_TRANSPORT = "GCM"

        private val pushTokenRequestBody = PushTokenBody(
            senderId = "7239",
            client = "cliId", token = MOCK_TOKEN, transport = MOCK_TRANSPORT
        )
    }
}
