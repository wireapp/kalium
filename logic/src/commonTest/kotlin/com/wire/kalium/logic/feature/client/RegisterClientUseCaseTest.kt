package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.data.prekey.PreKey
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.mock
import kotlin.test.BeforeTest
import kotlin.test.Test

class RegisterClientUseCaseTest {

    @Mock
    private val clientRepository = mock(classOf<ClientRepository>())

    private lateinit var registerClient: RegisterClientUseCase

    @BeforeTest
    fun setup() {
        registerClient = RegisterClientUseCase(clientRepository)
    }

    @Test
    fun givenRegistrationParams_whenRegistering_thenTheRepositoryShouldBeCalledWithCorrectParameters(){
        val params = REGISTER_PARAMETERS

        TODO()
    }

    @Test
    fun givenRepositoryRegistrationFailsDueToWrongCredentials_whenRegistering_thenInvalidCredentialsErrorShouldBeReturned(){
        TODO()
    }

    @Test
    fun givenRepositoryRegistrationFailsDueToGenericError_whenRegistering_thenGenericErrorShouldBeReturned(){
        TODO()
    }

    @Test
    fun givenRepositoryRegistrationFails_whenRegistering_thenNoPersistenceShouldBeDone(){
        TODO()
    }

    @Test
    fun givenPersistingClientIdFails_whenRegistering_thenTheFailureShouldBePropagated(){
        TODO()
    }

    @Test
    fun givenRegisteringSucceedsAndPersistingClientIdSucceeds_whenRegistering_thenTheFailureShouldBePropagated(){
        TODO()
    }


    private companion object{
        val REGISTER_PARAMETERS = RegisterClientParam("pass", listOf(), PreKey(2, "42"), null)
    }
}
