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
import com.wire.kalium.logic.di.UserSessionBinding
import com.wire.kalium.logic.feature.channels.ChannelsFeatureConfigurationHandler
import com.wire.kalium.logic.feature.channels.LegacyChannelsBinding
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
import dev.zacsweers.metro.Inject

/** Fetch and apply the team's feature settings. */
internal interface SyncFeatureConfigsUseCase {
    /** Apply every setting, returning the first failure after the refresh finishes. */
    suspend operator fun invoke(): Either<CoreFailure, Unit>

    /** Persist server policy before a client exists, stopping on failure. Slow sync updates protocols after registration. */
    suspend fun beforeClientRegistration(): Either<CoreFailure, Unit>
}

@Inject
@Suppress("LongParameterList")
internal class SyncFeatureConfigsUseCaseImpl(
    @UserSessionBinding("featureConfigRepository")
    private val featureConfigRepository: FeatureConfigRepository,
    @UserSessionBinding("guestRoomConfigHandler")
    private val guestRoomConfigHandler: GuestRoomConfigHandler,
    @UserSessionBinding("fileSharingConfigHandler")
    private val fileSharingConfigHandler: FileSharingConfigHandler,
    @UserSessionBinding("mlsConfigHandler")
    private val mlsConfigHandler: MLSConfigHandler,
    @UserSessionBinding("mlsMigrationConfigHandler")
    private val mlsMigrationConfigHandler: MLSMigrationConfigHandler,
    @UserSessionBinding("classifiedDomainsConfigHandler")
    private val classifiedDomainsConfigHandler: ClassifiedDomainsConfigHandler,
    @UserSessionBinding("conferenceCallingConfigHandler")
    private val conferenceCallingConfigHandler: ConferenceCallingConfigHandler,
    @UserSessionBinding("secondFactorPasswordChallengeConfigHandler")
    private val passwordChallengeConfigHandler: SecondFactorPasswordChallengeConfigHandler,
    @UserSessionBinding("selfDeletingMessagesConfigHandler")
    private val selfDeletingMessagesConfigHandler: SelfDeletingMessagesConfigHandler,
    @UserSessionBinding("e2eiConfigHandler")
    private val e2EIConfigHandler: E2EIConfigHandler,
    @UserSessionBinding("appLockConfigHandler")
    private val appLockConfigHandler: AppLockConfigHandler,
    @LegacyChannelsBinding("channelsFeatureConfigHandler")
    private val channelsConfigHandler: ChannelsFeatureConfigurationHandler,
    @UserSessionBinding("consumableNotificationsConfigHandler")
    private val consumableNotificationsConfigHandler: ConsumableNotificationsConfigHandler,
    @UserSessionBinding("allowedGlobalOperationsHandler")
    private val allowedGlobalOperationsHandler: AllowedGlobalOperationsHandler,
    @UserSessionBinding("cellsConfigHandler")
    private val cellsConfigHandler: CellsConfigHandler,
    @UserSessionBinding("appsFeatureHandler")
    private val appsFeatureHandler: AppsFeatureHandler,
    @UserSessionBinding("enableUserProfileQRCodeConfigHandler")
    private val enableUserProfileQRCodeConfigHandler: EnableUserProfileQRCodeConfigHandler,
    @UserSessionBinding("assetAuditLogConfigHandler")
    private val assetAuditLogConfigHandler: AssetAuditLogConfigHandler,
    @UserSessionBinding("preventAdminlessGroupsConfigHandler")
    private val preventAdminlessGroupsConfigHandler: PreventAdminlessGroupsConfigHandler,
    @UserSessionBinding("meetingsConfigHandler")
    private val meetingsConfigHandler: MeetingsConfigHandler,
) : SyncFeatureConfigsUseCase {
    override suspend operator fun invoke(): Either<CoreFailure, Unit> = sync(duringClientRegistration = false)

    override suspend fun beforeClientRegistration(): Either<CoreFailure, Unit> = sync(duringClientRegistration = true)

    private suspend fun sync(duringClientRegistration: Boolean): Either<CoreFailure, Unit> =
        featureConfigRepository.getFeatureConfigs().flatMap { config ->
            var firstFailure: CoreFailure? = null
            suspend fun applyConfiguration(handler: suspend () -> Either<CoreFailure, Unit>) {
                if (duringClientRegistration && firstFailure != null) return
                handler().onFailure { failure ->
                    if (firstFailure == null) firstFailure = failure
                }
            }

            // TODO handle other feature flags and after it bump version in [SlowSyncManager.CURRENT_VERSION]
            applyConfiguration {
                guestRoomConfigHandler.handle(config.guestRoomLinkModel, requirePreviousState = duringClientRegistration)
            }
            applyConfiguration {
                fileSharingConfigHandler.handle(config.fileSharingModel, requirePreviousState = duringClientRegistration)
            }
            applyConfiguration {
                mlsConfigHandler.handle(
                    config.mlsModel,
                    duringSlowSync = true,
                    duringClientRegistration = duringClientRegistration
                )
            }
            config.mlsMigrationModel?.let {
                applyConfiguration { mlsMigrationConfigHandler.handle(it, duringSlowSync = true) }
            }
            applyConfiguration { classifiedDomainsConfigHandler.handle(config.classifiedDomainsModel) }
            applyConfiguration { conferenceCallingConfigHandler.handle(config.conferenceCallingModel) }
            applyConfiguration { passwordChallengeConfigHandler.handle(config.secondFactorPasswordChallengeModel) }
            applyConfiguration {
                selfDeletingMessagesConfigHandler.handle(config.selfDeletingMessagesModel, requirePreviousState = duringClientRegistration)
            }
            applyConfiguration { e2EIConfigHandler.handle(config.e2EIModel) }
            applyConfiguration {
                appLockConfigHandler.handle(config.appLockModel, requirePreviousState = duringClientRegistration)
            }
            applyConfiguration { channelsConfigHandler.handle(config.channelsModel) }
            config.consumableNotificationsModel?.let {
                applyConfiguration { consumableNotificationsConfigHandler.handle(it) }
            }
            config.allowedGlobalOperationsModel?.let {
                applyConfiguration { allowedGlobalOperationsHandler.handle(it) }
            }
            applyConfiguration { cellsConfigHandler.handle(config.cellsModel) }
            applyConfiguration { cellsConfigHandler.handle(config.cellsInternalModel) }
            config.appsModel?.let { applyConfiguration { appsFeatureHandler.handle(it) } }
            applyConfiguration { enableUserProfileQRCodeConfigHandler.handle(config.enableUserProfileQRCodeConfigModel) }
            applyConfiguration { assetAuditLogConfigHandler.handle(config.assetAuditLogConfigModel) }
            applyConfiguration { preventAdminlessGroupsConfigHandler.handle(config.preventAdminlessGroupsModel) }
            applyConfiguration { meetingsConfigHandler.handle(config.meetingsConfigModel) }
            firstFailure?.let { Either.Left(it) } ?: Either.Right(Unit)
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
