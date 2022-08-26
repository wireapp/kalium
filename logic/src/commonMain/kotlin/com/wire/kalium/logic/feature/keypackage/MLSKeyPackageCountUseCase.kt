package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.functional.fold

interface MLSKeyPackageCountUseCase {
    suspend operator fun invoke(fromAPI: Boolean = true): MLSKeyPackageCountResult
}

class MLSKeyPackageCountUseCaseImpl(
    private val keyPackageRepository: KeyPackageRepository,
    private val clientRepository: ClientRepository,
    private val keyPackageLimitsProvider: KeyPackageLimitsProvider,
) : MLSKeyPackageCountUseCase {
    override suspend operator fun invoke(fromAPI: Boolean): MLSKeyPackageCountResult =
        when (fromAPI) {
            true -> validKeyPackagesCountFromAPI()
            false -> validKeyPackagesCountFromMLSClient()
        }

    private suspend fun validKeyPackagesCountFromAPI() = clientRepository.currentClientId().fold({
        MLSKeyPackageCountResult.Failure.FetchClientIdFailure(it)
    }, { selfClient ->
        keyPackageRepository.getAvailableKeyPackageCount(selfClient).fold(
            {
                MLSKeyPackageCountResult.Failure.NetworkCallFailure(it)
            }, { MLSKeyPackageCountResult.Success(selfClient, it.count, keyPackageLimitsProvider.needsRefill(it.count)) })
    })

    private suspend fun validKeyPackagesCountFromMLSClient() =
        clientRepository.currentClientId().fold({
            MLSKeyPackageCountResult.Failure.FetchClientIdFailure(it)
        }, { selfClient ->
            keyPackageRepository.validKeyPackageCount(selfClient).fold(
                {
                    MLSKeyPackageCountResult.Failure.Generic(it)
                }, { MLSKeyPackageCountResult.Success(selfClient, it, keyPackageLimitsProvider.needsRefill(it)) })
        })
}

sealed class MLSKeyPackageCountResult {
    data class Success(val clientId: ClientId, val count: Int, val needsRefill: Boolean) : MLSKeyPackageCountResult()

    sealed class Failure : MLSKeyPackageCountResult() {
        class NetworkCallFailure(val networkFailure: NetworkFailure) : Failure()
        class FetchClientIdFailure(val genericFailure: CoreFailure) : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
