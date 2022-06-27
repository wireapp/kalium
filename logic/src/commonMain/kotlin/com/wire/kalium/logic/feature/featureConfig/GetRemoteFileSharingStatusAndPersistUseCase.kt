package com.wire.kalium.logic.feature.featureConfig

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigModel
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.feature.user.IsFileSharingEnabledUseCase
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isNoTeam

/**
 * This use case is to get the file sharing status of the team management settings from the server and
 * save it in the local storage (in Android case is shared preference)
 */
interface GetRemoteFeatureConfigStatusAndPersistUseCase {
    suspend operator fun invoke(): GetFeatureConfigStatusResult
}

class GetFeatureConfigStatusUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
    private val featureConfigRepository: FeatureConfigRepository,
    private val isFileSharingEnabledUseCase: IsFileSharingEnabledUseCase
) : GetRemoteFeatureConfigStatusAndPersistUseCase {
    override suspend operator fun invoke(): GetFeatureConfigStatusResult =
        featureConfigRepository.getFeatureConfigs().fold({
            mapFeatureConfigFailure(it)
        }, {
            checkFileSharingStatus(it)
            // todo : handle other feature flags
        })

    private fun checkFileSharingStatus(featureConfigModel: FeatureConfigModel): GetFeatureConfigStatusResult {
        val status: Boolean = featureConfigModel.fileSharingModel.status.lowercase() == ENABLED
        return if (status == isFileSharingEnabledUseCase()) {
            GetFeatureConfigStatusResult.Success(featureConfigModel, false)
        } else {
            userConfigRepository.setFileSharingStatus(status)
            GetFeatureConfigStatusResult.Success(featureConfigModel, true)
        }
    }

    private fun mapFeatureConfigFailure(networkFailure: NetworkFailure): GetFeatureConfigStatusResult {
        return if (
            networkFailure is NetworkFailure.ServerMiscommunication &&
            networkFailure.kaliumException is KaliumException.InvalidRequestError
        ) {
            if (networkFailure.kaliumException.isNoTeam()) {
                GetFeatureConfigStatusResult.Failure.NoTeam
            } else {
                GetFeatureConfigStatusResult.Failure.OperationDenied
            }
        } else {
            GetFeatureConfigStatusResult.Failure.Generic(networkFailure)
        }

    }

    companion object {
        const val ENABLED = "enabled"
    }
}

sealed class GetFeatureConfigStatusResult {
    class Success(val featureConfigModel: FeatureConfigModel, val isStatusChanged: Boolean) : GetFeatureConfigStatusResult()
    sealed class Failure : GetFeatureConfigStatusResult() {
        object OperationDenied : Failure()
        object NoTeam : Failure()
        class Generic(val failure: NetworkFailure) : Failure()
    }
}
