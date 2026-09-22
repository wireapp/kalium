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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.feature.channels.ChannelsFeatureConfigurationHandler
import com.wire.kalium.logic.feature.featureConfig.handler.AppLockConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.AppsFeatureHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ClassifiedDomainsConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ConferenceCallingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ConsumableNotificationsConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.E2EIConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.FileSharingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.GuestRoomConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSMigrationConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.SecondFactorPasswordChallengeConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.SelfDeletingMessagesConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.AllowedGlobalOperationsHandler
import com.wire.kalium.logic.sync.receiver.handler.AssetAuditLogConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.CellsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.EnableUserProfileQRCodeConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.MeetingsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.PreventAdminlessGroupsConfigHandler
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isNoTeam

/**
 * This use case is to get the file sharing status of the team management settings from the server and
 * save it in the local storage (in Android case is shared preference)
 */
internal interface SyncFeatureConfigsUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>

    /** Persist server policy before a client exists. Initial slow sync performs protocol updates after registration. */
    suspend fun beforeClientRegistration(): Either<CoreFailure, Unit>
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
    private val appLockConfigHandler: AppLockConfigHandler,
    private val channelsConfigHandler: ChannelsFeatureConfigurationHandler,
    private val consumableNotificationsConfigHandler: ConsumableNotificationsConfigHandler,
    private val allowedGlobalOperationsHandler: AllowedGlobalOperationsHandler,
    private val cellsConfigHandler: CellsConfigHandler,
    private val appsFeatureHandler: AppsFeatureHandler,
    private val enableUserProfileQRCodeConfigHandler: EnableUserProfileQRCodeConfigHandler,
    private val assetAuditLogConfigHandler: AssetAuditLogConfigHandler,
    private val preventAdminlessGroupsConfigHandler: PreventAdminlessGroupsConfigHandler,
    private val meetingsConfigHandler: MeetingsConfigHandler,
) : SyncFeatureConfigsUseCase {
    override suspend operator fun invoke(): Either<CoreFailure, Unit> = sync(duringClientRegistration = false)

    override suspend fun beforeClientRegistration(): Either<CoreFailure, Unit> = sync(duringClientRegistration = true)

    private suspend fun sync(duringClientRegistration: Boolean): Either<CoreFailure, Unit> =
        featureConfigRepository.getFeatureConfigs().flatMap { config ->
            // TODO handle other feature flags and after it bump version in [SlowSyncManager.CURRENT_VERSION]
            guestRoomConfigHandler.handle(config.guestRoomLinkModel).onFailure { return@flatMap Either.Left(it) }
            fileSharingConfigHandler.handle(config.fileSharingModel).onFailure { return@flatMap Either.Left(it) }
            mlsConfigHandler.handle(
                config.mlsModel,
                duringSlowSync = true,
                duringClientRegistration = duringClientRegistration
            ).onFailure { return@flatMap Either.Left(it) }
            config.mlsMigrationModel?.let { mlsMigrationConfigHandler.handle(it, duringSlowSync = true) }
                ?.onFailure { return@flatMap Either.Left(it) }
            classifiedDomainsConfigHandler.handle(config.classifiedDomainsModel).onFailure { return@flatMap Either.Left(it) }
            conferenceCallingConfigHandler.handle(config.conferenceCallingModel).onFailure { return@flatMap Either.Left(it) }
            passwordChallengeConfigHandler.handle(config.secondFactorPasswordChallengeModel).onFailure {
                return@flatMap Either.Left(it)
            }
            selfDeletingMessagesConfigHandler.handle(config.selfDeletingMessagesModel).onFailure { return@flatMap Either.Left(it) }
            e2EIConfigHandler.handle(config.e2EIModel).onFailure { return@flatMap Either.Left(it) }
            appLockConfigHandler.handle(config.appLockModel).onFailure { return@flatMap Either.Left(it) }
            channelsConfigHandler.handle(config.channelsModel).onFailure { return@flatMap Either.Left(it) }
            config.consumableNotificationsModel?.let { consumableNotificationsConfigHandler.handle(it) }
                ?.onFailure { return@flatMap Either.Left(it) }
            config.allowedGlobalOperationsModel?.let { allowedGlobalOperationsHandler.handle(it) }
                ?.onFailure { return@flatMap Either.Left(it) }
            cellsConfigHandler.handle(config.cellsModel).onFailure { return@flatMap Either.Left(it) }
            cellsConfigHandler.handle(config.cellsInternalModel).onFailure { return@flatMap Either.Left(it) }
            config.appsModel?.let { appsFeatureHandler.handle(it) }?.onFailure { return@flatMap Either.Left(it) }
            enableUserProfileQRCodeConfigHandler.handle(config.enableUserProfileQRCodeConfigModel)
                .onFailure { return@flatMap Either.Left(it) }
            assetAuditLogConfigHandler.handle(config.assetAuditLogConfigModel).onFailure { return@flatMap Either.Left(it) }
            preventAdminlessGroupsConfigHandler.handle(config.preventAdminlessGroupsModel).onFailure {
                return@flatMap Either.Left(it)
            }
            meetingsConfigHandler.handle(config.meetingsConfigModel)
        }.onFailure { networkFailure ->
            if (
                networkFailure is NetworkFailure.ServerMiscommunication &&
                networkFailure.kaliumException is KaliumException.InvalidRequestError
            ) {
                if ((networkFailure.kaliumException as KaliumException.InvalidRequestError).isNoTeam()) {
                    kaliumLogger.i("this user doesn't belong to a team")
                } else {
                    kaliumLogger.d("operation denied due to insufficient permissions")
                }
            } else {
                kaliumLogger.d("$networkFailure")
            }
        }
}
