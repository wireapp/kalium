/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.featureConfig

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.ClassifiedDomainsModel
import com.wire.kalium.logic.data.featureConfig.ConferenceCallingModel
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.user.UserId
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
    private val isFileSharingEnabledUseCase: IsFileSharingEnabledUseCase,
    private val kaliumConfigs: KaliumConfigs,
    private val selfUserId: UserId
) : SyncFeatureConfigsUseCase {
    override suspend operator fun invoke(): Either<CoreFailure, Unit> =
        featureConfigRepository.getFeatureConfigs().flatMap {
            // TODO handle other feature flags
            handleFileSharingStatus(it.fileSharingModel)
            handleMLSStatus(it.mlsModel)
            handleClassifiedDomainsStatus(it.classifiedDomainsModel)
            handleConferenceCalling(it.conferenceCallingModel)
            handlePasswordChallengeStatus(it.secondFactorPasswordChallengeModel)
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

    private fun handleConferenceCalling(model: ConferenceCallingModel) {
        val conferenceCallingEnabled = model.status == Status.ENABLED
        userConfigRepository.setConferenceCallingEnabled(conferenceCallingEnabled)
    }

    private fun handlePasswordChallengeStatus(model: ConfigsStatusModel) {
        val isRequired = model.status == Status.ENABLED
        userConfigRepository.setSecondFactorPasswordChallengeStatus(isRequired)
    }

    private fun handleClassifiedDomainsStatus(model: ClassifiedDomainsModel) {
        val classifiedDomainsEnabled = model.status == Status.ENABLED
        userConfigRepository.setClassifiedDomainsStatus(classifiedDomainsEnabled, model.config.domains)
    }

    private fun handleFileSharingStatus(model: ConfigsStatusModel) {
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

    private fun handleMLSStatus(featureConfig: MLSModel) {
        val mlsEnabled = featureConfig.status == Status.ENABLED
        val selfUserIsWhitelisted = featureConfig.allowedUsers.contains(selfUserId.toPlainID())
        userConfigRepository.setMLSEnabled(mlsEnabled && selfUserIsWhitelisted)
    }
}
