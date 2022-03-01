package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientCapability
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.failure.ClientFailure
import com.wire.kalium.logic.feature.client.RegisterClientUseCase.Companion.FIRST_KEY_ID
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending

sealed class RegisterClientResult {
    class Success(val client: Client) : RegisterClientResult()

    sealed class Failure : RegisterClientResult() {
        object InvalidCredentials : Failure()
        object TooManyClients : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}


interface RegisterClientUseCase {
    suspend operator fun invoke(
        password: String,
        capabilities: List<ClientCapability>?,
        preKeysToSend: Int = DEFAULT_PRE_KEYS_COUNT
    ): RegisterClientResult

    companion object {
        const val FIRST_KEY_ID = 0
        const val DEFAULT_PRE_KEYS_COUNT = 100
    }
}

class RegisterClientUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val preKeyRepository: PreKeyRepository
) : RegisterClientUseCase {

    override suspend operator fun invoke(
        password: String,
        capabilities: List<ClientCapability>?,
        preKeysToSend: Int
    ): RegisterClientResult = suspending {

        preKeyRepository.generateNewPreKeys(FIRST_KEY_ID, preKeysToSend).flatMap { preKeys ->
            preKeyRepository.generateNewLastKey().flatMap { lastKey ->
                Either.Right(
                    RegisterClientParam(
                        password = password,
                        capabilities = capabilities,
                        preKeys = preKeys,
                        lastKey = lastKey
                    )
                )
            }
        }.coFold({
            RegisterClientResult.Failure.Generic(it)
        }, { registerClientParam ->
            clientRepository.registerClient(registerClientParam).flatMap { client ->
                clientRepository.persistClientId(client.clientId).map {
                    client
                }
            }.fold({ failure ->
                when (failure) {
                    ClientFailure.WrongPassword -> RegisterClientResult.Failure.InvalidCredentials
                    ClientFailure.TooManyClients -> RegisterClientResult.Failure.TooManyClients
                    else -> RegisterClientResult.Failure.Generic(failure)
                }
            }, { client ->
                RegisterClientResult.Success(client)
            })
        })
    }
}
