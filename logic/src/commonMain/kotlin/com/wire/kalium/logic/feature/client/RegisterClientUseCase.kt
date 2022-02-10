package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.failure.ClientFailure
import com.wire.kalium.logic.functional.suspending

interface RegisterClientUseCase {
    suspend operator fun invoke(param: RegisterClientParam): RegisterClientResult
}

class RegisterClientUseCaseImpl(private val clientRepository: ClientRepository) : RegisterClientUseCase {

    override suspend operator fun invoke(param: RegisterClientParam): RegisterClientResult = suspending {
        //TODO Should we fail here if the client is already registered?
        clientRepository.registerClient(param).flatMap { client ->
            clientRepository.persistClientId(client.clientId).map {
                client
            }
        }
    }.fold({ failure ->
        when (failure) {
            ClientFailure.WrongPassword -> RegisterClientResult.Failure.InvalidCredentials
            ClientFailure.TooManyClients -> RegisterClientResult.Failure.TooManyClients
            else -> RegisterClientResult.Failure.Generic(failure)
        }
    }, { client ->
        RegisterClientResult.Success(client)
    })!!

}
