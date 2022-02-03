package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.failure.WrongPassword
import com.wire.kalium.logic.functional.suspending


class RegisterClientUseCase(private val clientRepository: ClientRepository) {

    suspend operator fun invoke(param: RegisterClientParam): RegisterClientResult = suspending {
        //TODO Should we fail here if the client is already registered?
        clientRepository.registerClient(param).flatMap { client ->
            clientRepository.persistClientId(client.clientId).map {
                client
            }
        }
    }.fold({ failure ->
        if (failure is WrongPassword)
            RegisterClientResult.Failure.InvalidCredentials
        else
            RegisterClientResult.Failure.Generic(failure)
    }, { client ->
        RegisterClientResult.Success(client)
    })!!

}
