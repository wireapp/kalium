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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.SelfDeletingMessagesStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventLoggingStatus
import com.wire.kalium.logic.data.event.logEventProcessing
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

internal interface FeatureConfigEventReceiver : EventReceiver<Event.FeatureConfig>

internal class FeatureConfigEventReceiverImpl internal constructor(
    private val userConfigRepository: UserConfigRepository,
    private val kaliumConfigs: KaliumConfigs,
    private val selfUserId: UserId
) : FeatureConfigEventReceiver {

    override suspend fun onEvent(event: Event.FeatureConfig) {
        handleFeatureConfigEvent(event)
    }

    @Suppress("LongMethod")
    private fun handleFeatureConfigEvent(event: Event.FeatureConfig) {
        when (event) {
            is Event.FeatureConfig.FileSharingUpdated -> {
                val currentFileSharingStatus: Boolean = userConfigRepository
                    .isFileSharingEnabled()
                    .fold({ false }, {
                        when (it.state) {
                            FileSharingStatus.Value.Disabled -> false
                            FileSharingStatus.Value.EnabledAll -> true
                            is FileSharingStatus.Value.EnabledSome -> true
                        }
                    })

                when (event.model.status) {
                    Status.ENABLED -> userConfigRepository.setFileSharingStatus(
                        status = true,
                        isStatusChanged = !currentFileSharingStatus
                    )

                    Status.DISABLED -> userConfigRepository.setFileSharingStatus(
                        status = false,
                        isStatusChanged = currentFileSharingStatus
                    )
                }
                kaliumLogger.logEventProcessing(
                    EventLoggingStatus.SUCCESS,
                    event
                )
            }

            is Event.FeatureConfig.MLSUpdated -> {
                val mlsEnabled = event.model.status == Status.ENABLED
                val selfUserIsWhitelisted = event.model.allowedUsers.contains(selfUserId.toPlainID())
                userConfigRepository.setMLSEnabled(mlsEnabled && selfUserIsWhitelisted)

                kaliumLogger.logEventProcessing(
                    EventLoggingStatus.SUCCESS,
                    event
                )
            }

            is Event.FeatureConfig.ClassifiedDomainsUpdated -> {
                val classifiedDomainsEnabled = event.model.status == Status.ENABLED
                userConfigRepository.setClassifiedDomainsStatus(classifiedDomainsEnabled, event.model.config.domains)

                kaliumLogger.logEventProcessing(
                    EventLoggingStatus.SUCCESS,
                    event
                )
            }

            is Event.FeatureConfig.ConferenceCallingUpdated -> {
                val conferenceCallingEnabled = event.model.status == Status.ENABLED
                userConfigRepository.setConferenceCallingEnabled(conferenceCallingEnabled)

                kaliumLogger.logEventProcessing(
                    EventLoggingStatus.SUCCESS,
                    event
                )
            }

            is Event.FeatureConfig.GuestRoomLinkUpdated -> {
                handleGuestRoomLinkFeatureConfig(event.model.status)

                kaliumLogger.logEventProcessing(
                    EventLoggingStatus.SUCCESS,
                    event
                )
            }

            is Event.FeatureConfig.SelfDeletingMessagesConfig -> {
                handleSelfDeletingFeatureConfig(event.model.status, event.model.config.enforcedTimeoutSeconds)

                kaliumLogger.logEventProcessing(
                    EventLoggingStatus.SUCCESS,
                    event
                )
            }

            is Event.FeatureConfig.UnknownFeatureUpdated -> {
                kaliumLogger.logEventProcessing(
                    EventLoggingStatus.SKIPPED,
                    event,
                    Pair("info", "Ignoring unknown feature config update")
                )
            }
        }
    }

    private fun handleGuestRoomLinkFeatureConfig(status: Status) {
        if (!kaliumConfigs.guestRoomLink) {
            userConfigRepository.setGuestRoomStatus(false, null)
        } else {
            val currentGuestRoomStatus: Boolean = userConfigRepository
                .getGuestRoomLinkStatus()
                .fold({ true }, { it.isGuestRoomLinkEnabled ?: true })

            when (status) {
                Status.ENABLED -> userConfigRepository.setGuestRoomStatus(
                    status = true,
                    isStatusChanged = !currentGuestRoomStatus
                )

                Status.DISABLED -> userConfigRepository.setGuestRoomStatus(
                    status = false,
                    isStatusChanged = currentGuestRoomStatus
                )
            }
        }
    }

    private fun handleSelfDeletingFeatureConfig(status: Status, enforcedTimeoutSeconds: Int?) {
        if (!kaliumConfigs.selfDeletingMessages) {
            userConfigRepository.setSelfDeletingMessagesStatus(SelfDeletingMessagesStatus(false, null, null))
        } else {
            val (currentIsSelfDeletingMessagesEnabled, currentEnforcedTimeout) = userConfigRepository
                .getSelfDeletingMessagesStatus()
                .fold({ true to null }, { it.isEnabled to it.enforcedTimeoutInSeconds })

            when (status) {
                Status.ENABLED -> userConfigRepository.setSelfDeletingMessagesStatus(
                    SelfDeletingMessagesStatus(
                        isEnabled = true,
                        isStatusChanged = !currentIsSelfDeletingMessagesEnabled || currentEnforcedTimeout != enforcedTimeoutSeconds,
                        enforcedTimeoutInSeconds = if (enforcedTimeoutSeconds == 0) null else enforcedTimeoutSeconds
                    )
                )

                Status.DISABLED -> userConfigRepository.setSelfDeletingMessagesStatus(
                    SelfDeletingMessagesStatus(
                        isEnabled = false,
                        isStatusChanged = currentIsSelfDeletingMessagesEnabled,
                        enforcedTimeoutInSeconds = enforcedTimeoutSeconds
                    )
                )
            }
        }
    }
}
