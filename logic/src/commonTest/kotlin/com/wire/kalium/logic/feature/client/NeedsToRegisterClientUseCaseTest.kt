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

@OptIn(ConfigurationApi::class, ExperimentalCoroutinesApi::class)
class NeedsToRegisterClientUseCaseTest {

    @Mock
    private val clientRepository = configure(mock(classOf<ClientRepository>())) {
        stubsUnitByDefault = true
    }

    private lateinit var needsToRegisterClientUseCase: NeedsToRegisterClientUseCase

    @BeforeTest
    fun setup() {
        needsToRegisterClientUseCase = NeedsToRegisterClientUseCaseImpl(clientRepository)
    }

    @Test
    fun givenClientIdIsRegistered_thenReturnFalse() = runTest {
        given(clientRepository)
            .invocation { clientRepository.currentClientId() }
            .then { Either.Right(CLIENT_ID) }

        val actual = needsToRegisterClientUseCase.invoke()
        assertEquals(actual, false)
    }

    @Test
    fun givenClientIdIsNotRegistered_thenReturnTrue() = runTest {
        given(clientRepository)
            .invocation { clientRepository.currentClientId() }
            .then { Either.Left(CoreFailure.MissingClientRegistration) }

        val actual = needsToRegisterClientUseCase.invoke()
        assertEquals(actual, true)
    }

    private companion object {
        val CLIENT_ID: ClientId = ClientId("client_id")
    }
}
