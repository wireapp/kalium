package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
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
    private val persistRegisteredClientIdUseCase: PersistRegisteredClientIdUseCase
) : GetOrRegisterClientUseCase {

    override suspend fun invoke(registerClientParam: RegisterClientUseCase.RegisterClientParam): RegisterClientResult {
        val result: RegisterClientResult? = clientRepository.retainedClientId()
            .nullableFold(
                {
                    if (it is CoreFailure.MissingClientRegistration) null
                    else RegisterClientResult.Failure.Generic(it)
                }, { retainedClientId ->
                    when (val result = persistRegisteredClientIdUseCase(retainedClientId)) {
                        is PersistRegisteredClientIdResult.Success -> RegisterClientResult.Success(result.client)
                        is PersistRegisteredClientIdResult.Failure.Generic -> RegisterClientResult.Failure.Generic(result.genericFailure)
                        is PersistRegisteredClientIdResult.Failure.ClientNotRegistered -> {
                            clearClientData()
                            clientRepository.clearRetainedClientId()
                            null
                        }
                    }
                }
            )
        return result ?: registerClient(registerClientParam)
    }
}
