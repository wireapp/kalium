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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.feature.featureConfig.handler.AppLockConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ClassifiedDomainsConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ConferenceCallingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.E2EIConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.FileSharingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.GuestRoomConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.MLSMigrationConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.SelfDeletingMessagesConfigHandler
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.EventLoggingStatus
import com.wire.kalium.logic.util.createEventProcessingLogger

internal interface FeatureConfigEventReceiver : EventReceiver<Event.FeatureConfig>

@Suppress("LongParameterList")
internal class FeatureConfigEventReceiverImpl internal constructor(
    private val guestRoomConfigHandler: GuestRoomConfigHandler,
    private val fileSharingConfigHandler: FileSharingConfigHandler,
    private val mlsConfigHandler: MLSConfigHandler,
    private val mlsMigrationConfigHandler: MLSMigrationConfigHandler,
    private val classifiedDomainsConfigHandler: ClassifiedDomainsConfigHandler,
    private val conferenceCallingConfigHandler: ConferenceCallingConfigHandler,
    private val selfDeletingMessagesConfigHandler: SelfDeletingMessagesConfigHandler,
    private val e2EIConfigHandler: E2EIConfigHandler,
    private val appLockConfigHandler: AppLockConfigHandler
) : FeatureConfigEventReceiver {

    override suspend fun onEvent(event: Event.FeatureConfig, deliveryInfo: EventDeliveryInfo): Either<CoreFailure, Unit> {
        val logger = kaliumLogger.createEventProcessingLogger(event)
        return handleFeatureConfigEvent(event)
            .onSuccess { logger.logSuccess() }
            .onFailure { logger.logFailure(it) }
    }

@Suppress("LongMethod", "ComplexMethod")
private suspend fun handleFeatureConfigEvent(event: Event.FeatureConfig): Either<CoreFailure, Unit> =
    when (event) {
        is Event.FeatureConfig.FileSharingUpdated -> fileSharingConfigHandler.handle(event.model)
        is Event.FeatureConfig.MLSUpdated -> mlsConfigHandler.handle(event.model, duringSlowSync = false)
        is Event.FeatureConfig.MLSMigrationUpdated -> mlsMigrationConfigHandler.handle(event.model, duringSlowSync = false)
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
    }
}
