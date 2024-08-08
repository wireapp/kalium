/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.feature.featureConfig.handler.AppLockConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ClassifiedDomainsConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ConferenceCallingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.E2EIConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.FileSharingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.GuestRoomConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSMigrationConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.SecondFactorPasswordChallengeConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.SelfDeletingMessagesConfigHandler
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

@Suppress("LongParameterList")
internal class SyncFeatureConfigsUseCaseImpl(
    private val featureConfigRepository: FeatureConfigRepository,
    private val guestRoomConfigHandler: GuestRoomConfigHandler,
    private val fileSharingConfigHandler: FileSharingConfigHandler,
    private val mlsConfigHandler: MLSConfigHandler,
    private val mlsMigrationConfigHandler: MLSMigrationConfigHandler,
    private val classifiedDomainsConfigHandler: ClassifiedDomainsConfigHandler,
    private val conferenceCallingConfigHandler: ConferenceCallingConfigHandler,
    private val passwordChallengeConfigHandler: SecondFactorPasswordChallengeConfigHandler,
    private val selfDeletingMessagesConfigHandler: SelfDeletingMessagesConfigHandler,
    private val e2EIConfigHandler: E2EIConfigHandler,
    private val appLockConfigHandler: AppLockConfigHandler
) : SyncFeatureConfigsUseCase {
    override suspend operator fun invoke(): Either<CoreFailure, Unit> =
        featureConfigRepository.getFeatureConfigs().flatMap { it ->
            // TODO handle other feature flags and after it bump version in [SlowSyncManager.CURRENT_VERSION]
            guestRoomConfigHandler.handle(it.guestRoomLinkModel)
            fileSharingConfigHandler.handle(it.fileSharingModel)
             mlsConfigHandler.handle(it.mlsModel, duringSlowSync = true)
            it.mlsMigrationModel?.let { mlsMigrationConfigHandler.handle(it, duringSlowSync = true) }
            classifiedDomainsConfigHandler.handle(it.classifiedDomainsModel)
            conferenceCallingConfigHandler.handle(it.conferenceCallingModel)
            passwordChallengeConfigHandler.handle(it.secondFactorPasswordChallengeModel)
            selfDeletingMessagesConfigHandler.handle(it.selfDeletingMessagesModel)
            it.e2EIModel.let { e2EIModel -> e2EIConfigHandler.handle(e2EIModel) }
            appLockConfigHandler.handle(it.appLockModel)
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
}
