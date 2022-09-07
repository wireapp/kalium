package com.wire.kalium.logic.feature.featureConfig

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.ClassifiedDomainsModel
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.user.IsFileSharingEnabledUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isNoTeam

/**
 * This use case is to get the file sharing status of the team management settings from the server and
 * save it in the local storage (in Android case is shared preference)
 */
internal interface SyncFeatureConfigsUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

internal class SyncFeatureConfigsUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
    private val featureConfigRepository: FeatureConfigRepository,
    private val userRepository: UserRepository,
    private val isFileSharingEnabledUseCase: IsFileSharingEnabledUseCase,
    private val kaliumConfigs: KaliumConfigs
) : SyncFeatureConfigsUseCase {
    override suspend operator fun invoke(): Either<CoreFailure, Unit> =
        featureConfigRepository.getFeatureConfigs().flatMap {
            // TODO handle other feature flags
            checkFileSharingStatus(it.fileSharingModel)
            checkMLSStatus(it.mlsModel)
            checkClassifiedDomainsStatus(it.classifiedDomainsModel)
            Either.Right(Unit)
        }.onFailure { networkFailure ->
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

    private fun checkClassifiedDomainsStatus(model: ClassifiedDomainsModel) {
        val classifiedDomainsEnabled = model.status == Status.ENABLED
        userConfigRepository.setClassifiedDomainsStatus(classifiedDomainsEnabled, model.config.domains)
    }

    private fun checkFileSharingStatus(model: ConfigsStatusModel) {
        if (kaliumConfigs.fileRestrictionEnabled) {
            userConfigRepository.setFileSharingStatus(false, null)
        } else {
            val status: Boolean = model.status == Status.ENABLED
            val isStatusChanged = when (isFileSharingEnabledUseCase().isFileSharingEnabled) {
                null, status -> false
                else -> true
            }
            userConfigRepository.setFileSharingStatus(status, isStatusChanged)
        }
    }

    private fun checkMLSStatus(featureConfig: MLSModel) {
        val mlsEnabled = featureConfig.status == Status.ENABLED
        val selfUserIsWhitelisted = featureConfig.allowedUsers.contains(userRepository.getSelfUserId().toPlainID())
        userConfigRepository.setMLSEnabled(mlsEnabled && selfUserIsWhitelisted)
    }
}
