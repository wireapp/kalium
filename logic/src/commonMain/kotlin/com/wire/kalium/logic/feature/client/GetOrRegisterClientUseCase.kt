package com.wire.kalium.logic.feature.client

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.nullableFold

interface GetOrRegisterClientUseCase {
    suspend operator fun invoke(
        registerClientParam: RegisterClientUseCase.RegisterClientParam
    ): RegisterClientResult
}

class GetOrRegisterClientUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val registerClient: RegisterClientUseCase,
    private val clearClientData: ClearClientDataUseCase,
    private val proteusClient: ProteusClient
) : GetOrRegisterClientUseCase {

    override suspend fun invoke(registerClientParam: RegisterClientUseCase.RegisterClientParam): RegisterClientResult {
        val result: RegisterClientResult? = clientRepository.retainedClientId()
            .flatMap { retainedClientId -> clientRepository.selfListOfClients().map { retainedClientId to it } }
            .nullableFold(
                {
                    if (it is CoreFailure.MissingClientRegistration) null
                    else RegisterClientResult.Failure.Generic(it)
                }, { (retainedClientId, listOfClients) ->
                    val client = listOfClients.firstOrNull { it.id == retainedClientId }
                    if (client != null) {
                        clientRepository.persistClientId(client.id)
                        RegisterClientResult.Success(client)
                    } else {
                        clearClientData()
                        proteusClient.open()
                        clientRepository.clearRetainedClientId()
                        null
                    }
                }
            )
        return result ?: registerClient(registerClientParam)
    }
}
