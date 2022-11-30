package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.functional.fold

interface ImportClientUseCase {

    /**
     * @param clientId client id of client
     * @param registerClientParam: register client parameters for the case when client isn't already registered
     * @return success if the client was successfully imported, failure otherwise
     */
    suspend operator fun invoke(clientId: ClientId, registerClientParam: RegisterClientUseCase.RegisterClientParam): RegisterClientResult
}

internal class ImportClientUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val getOrRegisterClient: GetOrRegisterClientUseCase
) : ImportClientUseCase {

    override suspend fun invoke(
        clientId: ClientId,
        registerClientParam: RegisterClientUseCase.RegisterClientParam
    ): RegisterClientResult {
        return clientRepository.persistRetainedClientId(clientId)
            .fold(
                { coreFailure ->
                    RegisterClientResult.Failure.Generic(coreFailure)
                },
                {
                    getOrRegisterClient(registerClientParam)
                }
            )
    }
}
