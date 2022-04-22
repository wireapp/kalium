package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.notification.pushToken.PushTokenRequestBody
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

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
    fun givenPushTokenParams_whenRegister_thenTheRepositoryShouldBeCalledWithCorrectParameters() = runTest {
        val pushTokenRequestBody = PushTokenRequestBody(
            senderId = "7239",
            client = "cliId", token = "7239", transport = "GCM"
        )

        given(eventRepository)
            .suspendFunction(eventRepository::registerToken)
            .whenInvokedWith(anything())
            .then { Either.Right(Unit) }

        pushTokenUseCase(pushTokenRequestBody)

        verify(eventRepository)
            .suspendFunction(eventRepository::registerToken)
            .with(eq(pushTokenRequestBody))
            .wasInvoked(once)
    }
}
