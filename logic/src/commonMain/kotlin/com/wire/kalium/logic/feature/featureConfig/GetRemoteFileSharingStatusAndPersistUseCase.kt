package com.wire.kalium.logic.feature.featureConfig

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigModel
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.feature.user.IsFileSharingEnabledUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isNoTeam

/**
 * This use case is to get the file sharing status of the team management settings from the server and
 * save it in the local storage (in Android case is shared preference)
 */
internal interface SyncFeatureConfigsUseCase {
    suspend operator fun invoke()
}

internal class SyncFeatureConfigsUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
    private val featureConfigRepository: FeatureConfigRepository,
    private val isFileSharingEnabledUseCase: IsFileSharingEnabledUseCase,
    private val kaliumConfigs: KaliumConfigs
) : SyncFeatureConfigsUseCase {
    override suspend operator fun invoke() {
        featureConfigRepository.getFeatureConfigs().fold({
            mapFeatureConfigFailure(it)
        }, {
            checkFileSharingStatus(it)
            // todo : handle other feature flags
        })
    }

    private fun checkFileSharingStatus(featureConfigModel: FeatureConfigModel) {
        if (kaliumConfigs.fileRestrictionEnabled) {
            userConfigRepository.setFileSharingStatus(false, null)
        } else {
            val status: Boolean = featureConfigModel.fileSharingModel.status.lowercase() == ENABLED
            if (status == isFileSharingEnabledUseCase().isFileSharingEnabled) {
                userConfigRepository.setFileSharingStatus(status, false)
            } else {
                userConfigRepository.setFileSharingStatus(status, true)
            }
        }
    }

    private fun mapFeatureConfigFailure(networkFailure: NetworkFailure) {
        if (
            networkFailure is NetworkFailure.ServerMiscommunication &&
            networkFailure.kaliumException is KaliumException.InvalidRequestError
        ) {
            if (networkFailure.kaliumException.isNoTeam()) {
                kaliumLogger.i("this user doesn't belong to a team")
            } else {
                kaliumLogger.d("operation denied due to insufficient permissions")
            }
        } else {
            kaliumLogger.d("$networkFailure")
        }
    }

    companion object {
        const val ENABLED = "enabled"
    }
}
