package com.wire.kalium.logic.feature.featureConfig

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.FileSharingModel
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isNoTeam

/**
 * This use case is to get the file sharing status of the team management settings from the server and
 * save it in the local storage (in Android case is shared preference)
 */
interface GetAndSaveFileSharingStatusUseCase {
    suspend operator fun invoke(): GetFileSharingStatusResult
}

class GetFileSharingStatusUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
    private val featureConfigRepository: FeatureConfigRepository
) : GetAndSaveFileSharingStatusUseCase {
    override suspend operator fun invoke(): GetFileSharingStatusResult =
        featureConfigRepository.getFileSharingFeatureConfig().fold({
            mapFileSharingFailure(it)
        }, {
            userConfigRepository.setFileSharingStatus(it.status.lowercase() == ENABLED)
            GetFileSharingStatusResult.Success(it)
        })


    private fun mapFileSharingFailure(networkFailure: NetworkFailure): GetFileSharingStatusResult {
        return if (
            networkFailure is NetworkFailure.ServerMiscommunication &&
            networkFailure.kaliumException is KaliumException.InvalidRequestError
        ) {
            if (networkFailure.kaliumException.isNoTeam()) {
                GetFileSharingStatusResult.Failure.NoTeam
            } else {
                GetFileSharingStatusResult.Failure.OperationDenied
            }
        } else {
            GetFileSharingStatusResult.Failure.Generic(networkFailure)
        }

    }

    companion object {
        const val ENABLED = "enabled"
    }
}

sealed class GetFileSharingStatusResult {
    class Success(val fileSharingModel: FileSharingModel) : GetFileSharingStatusResult()
    sealed class Failure : GetFileSharingStatusResult() {
        object OperationDenied : Failure()
        object NoTeam : Failure()
        class Generic(val failure: NetworkFailure) : Failure()
    }
}
