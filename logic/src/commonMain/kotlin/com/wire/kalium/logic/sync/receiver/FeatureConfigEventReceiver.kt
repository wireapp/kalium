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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.di.UserSessionBinding
import com.wire.kalium.logic.feature.featureConfig.handler.AppLockConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ClassifiedDomainsConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ConferenceCallingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.E2EIConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.FileSharingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.GuestRoomConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSMigrationConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.SelfDeletingMessagesConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.AllowedGlobalOperationsHandler
import com.wire.kalium.logic.sync.receiver.handler.AssetAuditLogConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.CellsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.EnableUserProfileQRCodeConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.MeetingsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.PreventAdminlessGroupsConfigHandler
import com.wire.kalium.logic.util.EventLoggingStatus
import com.wire.kalium.logic.util.createEventProcessingLogger
import dev.zacsweers.metro.Inject

@Deprecated("These events are not received/sent to clients anymore", ReplaceWith("SyncFeatureConfigsUseCase"))
internal interface FeatureConfigEventReceiver : EventReceiver<Event.FeatureConfig>

@Inject
@Suppress("LongParameterList")
internal class FeatureConfigEventReceiverImpl internal constructor(
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
    @UserSessionBinding("selfDeletingMessagesConfigHandler")
    private val selfDeletingMessagesConfigHandler: SelfDeletingMessagesConfigHandler,
    @UserSessionBinding("e2eiConfigHandler")
    private val e2EIConfigHandler: E2EIConfigHandler,
    @UserSessionBinding("appLockConfigHandler")
    private val appLockConfigHandler: AppLockConfigHandler,
    @UserSessionBinding("allowedGlobalOperationsHandler")
    private val allowedGlobalOperationsHandler: AllowedGlobalOperationsHandler,
    @UserSessionBinding("cellsConfigHandler")
    private val cellsConfigHandler: CellsConfigHandler,
    @UserSessionBinding("enableUserProfileQRCodeConfigHandler")
    private val enableUserProfileQRCodeConfigHandler: EnableUserProfileQRCodeConfigHandler,
    @UserSessionBinding("assetAuditLogConfigHandler")
    private val assetAuditLogConfigHandler: AssetAuditLogConfigHandler,
    @UserSessionBinding("preventAdminlessGroupsConfigHandler")
    private val preventAdminlessGroupsConfigHandler: PreventAdminlessGroupsConfigHandler,
    @UserSessionBinding("meetingsConfigHandler")
    private val meetingsConfigHandler: MeetingsConfigHandler,
) : FeatureConfigEventReceiver {

    override suspend fun onEvent(
        transactionContext: CryptoTransactionContext,
        event: Event.FeatureConfig,
        deliveryInfo: EventDeliveryInfo
    ): Either<CoreFailure, Unit> {
        val logger = kaliumLogger.createEventProcessingLogger(event)
        return handleFeatureConfigEvent(event, transactionContext)
            .onSuccess { logger.logSuccess() }
            .onFailure { logger.logFailure(it) }
    }

    @Suppress("LongMethod", "ComplexMethod")
    private suspend fun handleFeatureConfigEvent(
        event: Event.FeatureConfig,
        transactionContext: CryptoTransactionContext
    ): Either<CoreFailure, Unit> =
        when (event) {
            is Event.FeatureConfig.FileSharingUpdated -> fileSharingConfigHandler.handle(event.model)
            is Event.FeatureConfig.MLSUpdated ->
                mlsConfigHandler.handle(
                    mlsConfig = event.model,
                    duringSlowSync = false,
                    transactionContext = transactionContext
                )
            is Event.FeatureConfig.MLSMigrationUpdated ->
                mlsMigrationConfigHandler.handle(
                    mlsMigrationConfig = event.model,
                    duringSlowSync = false,
                    transactionContext = transactionContext
                )
            is Event.FeatureConfig.ClassifiedDomainsUpdated -> classifiedDomainsConfigHandler.handle(event.model)
            is Event.FeatureConfig.ConferenceCallingUpdated -> conferenceCallingConfigHandler.handle(event.model)
            is Event.FeatureConfig.GuestRoomLinkUpdated -> guestRoomConfigHandler.handle(event.model)
            is Event.FeatureConfig.SelfDeletingMessagesConfig -> selfDeletingMessagesConfigHandler.handle(event.model)
            is Event.FeatureConfig.MLSE2EIUpdated -> e2EIConfigHandler.handle(event.model)
            is Event.FeatureConfig.AppLockUpdated -> appLockConfigHandler.handle(event.model)
            is Event.FeatureConfig.UnknownFeatureUpdated -> {
                kaliumLogger.createEventProcessingLogger(event).logComplete(
                    EventLoggingStatus.SKIPPED,
                    arrayOf("info" to "Ignoring unknown feature config update")
                )

                Either.Right(Unit)
            }

            is Event.FeatureConfig.AllowedGlobalOperationsUpdated -> allowedGlobalOperationsHandler.handle(event.model)
            is Event.FeatureConfig.CellsConfigUpdated -> cellsConfigHandler.handle(event.model)
            is Event.FeatureConfig.CellsInternalConfigUpdated -> cellsConfigHandler.handle(event.model)
            is Event.FeatureConfig.EnableUserProfileQRCodeConfigUpdated ->
                enableUserProfileQRCodeConfigHandler.handle(event.model)
            is Event.FeatureConfig.AssetAuditLogConfigUpdated -> assetAuditLogConfigHandler.handle(event.model)
            is Event.FeatureConfig.PreventAdminlessGroupsConfigUpdated ->
                preventAdminlessGroupsConfigHandler.handle(event.model)
            is Event.FeatureConfig.MeetingsConfigUpdated -> meetingsConfigHandler.handle(event.model)
        }
}
