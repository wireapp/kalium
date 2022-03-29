package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.functional.Either
import io.mockative.ConfigurationApi
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ConfigurationApi::class, ExperimentalCoroutinesApi::class)
class CurrentClientIdUseCaseTest {

    @Mock
    private val clientRepository = configure(mock(classOf<ClientRepository>())) {
        stubsUnitByDefault = true
    }

    private lateinit var currentClientIdUseCase: CurrentClientIdUseCase

    @BeforeTest
    fun setup() {
        currentClientIdUseCase = CurrentClientIdUseCaseImpl(clientRepository)
    }

    @Test
    fun givenClientIdIsRegistered_thenReturnSuccess() = runTest {
        given(clientRepository)
            .coroutine { clientRepository.currentClientId() }
            .then { Either.Right(CLIENT_ID) }

        val actual = currentClientIdUseCase.invoke()
        assertIs<CurrentClientIdResult.Success>(actual)
        assertEquals(CLIENT_ID, actual.clientId)
    }

    @Test
    fun givenClientIdIsNotRegistered_thenReturnMissingClientRegistration() = runTest {
        given(clientRepository)
            .coroutine { clientRepository.currentClientId() }
            .then { Either.Left(CoreFailure.MissingClientRegistration) }

        val actual = currentClientIdUseCase.invoke()
        assertIs<CurrentClientIdResult.MissingClientRegistration>(actual)
    }

    private companion object {
        val CLIENT_ID: ClientId = ClientId("client_id")
    }
}
