package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCase
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.nullableFold

/**
 * This use case is responsible for getting the client.
 * If the client is not found, it will be registered.
 */
interface GetOrRegisterClientUseCase {
    suspend operator fun invoke(
        registerClientParam: RegisterClientUseCase.RegisterClientParam
    ): RegisterClientResult
}

class GetOrRegisterClientUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val registerClient: RegisterClientUseCase,
    private val clearClientData: ClearClientDataUseCase,
    private val verifyExistingClientUseCase: VerifyExistingClientUseCase,
    private val upgradeCurrentSessionUseCase: UpgradeCurrentSessionUseCase,
) : GetOrRegisterClientUseCase {

    override suspend fun invoke(registerClientParam: RegisterClientUseCase.RegisterClientParam): RegisterClientResult {
        val result: RegisterClientResult = clientRepository.retainedClientId()
            .nullableFold(
                {
                    if (it is CoreFailure.MissingClientRegistration) null
                    else RegisterClientResult.Failure.Generic(it)
                }, { retainedClientId ->
                    when (val result = verifyExistingClientUseCase(retainedClientId)) {
                        is VerifyExistingClientResult.Success -> RegisterClientResult.Success(result.client)
                        is VerifyExistingClientResult.Failure.Generic -> RegisterClientResult.Failure.Generic(result.genericFailure)
                        is VerifyExistingClientResult.Failure.ClientNotRegistered -> {
                            clearClientData()
                            clientRepository.clearRetainedClientId()
                            null
                        }
                    }
                }
            ) ?: registerClient(registerClientParam)

        if (result is RegisterClientResult.Success) {
            upgradeCurrentSessionUseCase(result.client.id).flatMap {
                clientRepository.persistClientId(result.client.id)
            }
        }

        return result
    }
}
