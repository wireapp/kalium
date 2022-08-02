package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold

interface MLSKeyPackageCountUseCase {
    suspend operator fun invoke(): MLSKeyPackageCountResult
}

class MLSKeyPackageCountUseCaseImpl(
    private val keyPackageRepository: KeyPackageRepository,
    private val clientRepository: ClientRepository
) : MLSKeyPackageCountUseCase {
    override suspend operator fun invoke(): MLSKeyPackageCountResult =
        clientRepository.currentClientId().fold({
            MLSKeyPackageCountResult.Failure.FetchClientIdFailure(it)
        }, { selfClient ->
            keyPackageRepository.getAvailableKeyPackageCount(selfClient).fold(
                {
                    MLSKeyPackageCountResult.Failure.NetworkCallFailure(it)
                }, { MLSKeyPackageCountResult.Success(selfClient, it.count) })
        })

}


sealed class MLSKeyPackageCountResult {
    data class Success(val clientId: ClientId, val count: Int) : MLSKeyPackageCountResult()

    sealed class Failure : MLSKeyPackageCountResult() {
        class NetworkCallFailure(val networkFailure: NetworkFailure) : Failure()
        class FetchClientIdFailure(val genericFailure: CoreFailure) : Failure()
    }
}
