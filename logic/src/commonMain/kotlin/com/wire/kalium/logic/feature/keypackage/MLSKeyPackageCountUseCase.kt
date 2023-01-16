package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case will return the current number of key packages.
 */
interface MLSKeyPackageCountUseCase {
    suspend operator fun invoke(fromAPI: Boolean = true): MLSKeyPackageCountResult
}

class MLSKeyPackageCountUseCaseImpl(
    private val keyPackageRepository: KeyPackageRepository,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val keyPackageLimitsProvider: KeyPackageLimitsProvider,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : MLSKeyPackageCountUseCase {
    override suspend operator fun invoke(fromAPI: Boolean): MLSKeyPackageCountResult = withContext(dispatcher.default) {
        when (fromAPI) {
            true -> validKeyPackagesCountFromAPI()
            false -> validKeyPackagesCountFromMLSClient()
        }
    }

    private suspend fun validKeyPackagesCountFromAPI() = currentClientIdProvider().fold({
        MLSKeyPackageCountResult.Failure.FetchClientIdFailure(it)
    }, { selfClient ->
        keyPackageRepository.getAvailableKeyPackageCount(selfClient).fold(
            {
                MLSKeyPackageCountResult.Failure.NetworkCallFailure(it)
            }, { MLSKeyPackageCountResult.Success(selfClient, it.count, keyPackageLimitsProvider.needsRefill(it.count)) })
    })

    private suspend fun validKeyPackagesCountFromMLSClient() =
        currentClientIdProvider().fold({
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
