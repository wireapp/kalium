package com.wire.kalium.logic.feature.client

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.data.client.ClientCapability
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.data.prekey.PreKeyMapper
import com.wire.kalium.logic.failure.TooManyClients
import com.wire.kalium.logic.failure.WrongPassword
import com.wire.kalium.logic.functional.suspending

interface RegisterClientUseCase {
    suspend operator fun invoke(param: RegisterClientParam): RegisterClientResult
}

class RegisterClientUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val proteusClient: ProteusClient,
    private val preKeyMapper: PreKeyMapper
): RegisterClientUseCase {

    override suspend operator fun invoke(
        password: String,
        capabilities: List<ClientCapability>?,
        preKeysToSend: Int = DEFAULT_PRE_KEYS_COUNT
    ): RegisterClientResult = suspending {
        //TODO Should we fail here if the client is already registered?
        try {
            val param = RegisterClientParam(
                password = password,
                capabilities = capabilities,
                preKeys = proteusClient.newPreKeys(FIRST_KEY_ID, preKeysToSend),
                lastKey = proteusClient.newLastPreKey()
            )

            clientRepository.registerClient(param).flatMap { client ->
                clientRepository.persistClientId(client.clientId).map {
                    client
                }

            }.fold({ failure ->
                when (failure) {
                    WrongPassword -> RegisterClientResult.Failure.InvalidCredentials
                    TooManyClients -> RegisterClientResult.Failure.TooManyClients
                    else -> RegisterClientResult.Failure.Generic(failure)
                }
            }, { client ->
                RegisterClientResult.Success(client)
            })!!

        } catch (e: ProteusException) {
            RegisterClientResult.Failure.ProteusFailure(e)
        }
    }

    private companion object {
        const val FIRST_KEY_ID = 0
        const val DEFAULT_PRE_KEYS_COUNT = 100
    }

}
