package com.wire.kalium.logic.feature.featureConfig

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.FileSharingModel
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isNoTeam

interface GetFileSharingStatusUseCase {
    suspend operator fun invoke(): GetFileSharingStatusResult
}

class GetFileSharingStatusUseCaseImpl(
    private val featureConfigRepository: FeatureConfigRepository
) : GetFileSharingStatusUseCase {
    override suspend operator fun invoke(): GetFileSharingStatusResult =
        featureConfigRepository.getFileSharingFeatureConfig().fold({
            if (
                it is NetworkFailure.ServerMiscommunication &&
                it.kaliumException is KaliumException.InvalidRequestError
            ) {
                if (it.kaliumException.isNoTeam()) {
                    GetFileSharingStatusResult.Failure.NoTeam
                } else {
                    GetFileSharingStatusResult.Failure.OperationDenied
                }
            } else {
                GetFileSharingStatusResult.Failure.Generic(it)
            }

        }, {
            GetFileSharingStatusResult.Success(it)
        })
}

sealed class GetFileSharingStatusResult {
    class Success(val fileSharingModel: FileSharingModel) : GetFileSharingStatusResult()
    sealed class Failure : GetFileSharingStatusResult() {
        object OperationDenied : Failure()
        object NoTeam : Failure()
        class Generic(val failure: NetworkFailure) : Failure()

    }
}
