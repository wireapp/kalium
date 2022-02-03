package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.failure.AuthenticationFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


class RegisterClientUseCase(
    private val clientRepository: ClientRepository
) {
    suspend operator fun invoke(param: RegisterClientParam): Flow<RegisterClientResult> = flow {
        //TODO Should we fail here if the client is already registered?
        when (val result = clientRepository.registerClient(param)) {
            is Either.Right -> {
                suspending {
                    result.flatMap {
                        clientRepository.persistClientId(it.clientId)
                    }
                }
                emit(RegisterClientResult.Success(result.value))
            }

            is Either.Left -> {
                if (result.value is AuthenticationFailure)
                    emit(RegisterClientResult.Failure.InvalidCredentials)
                else
                    emit(RegisterClientResult.Failure.Generic(result.value))
            }
        }
    }
}
