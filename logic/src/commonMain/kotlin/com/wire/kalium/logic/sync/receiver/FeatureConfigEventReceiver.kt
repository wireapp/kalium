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

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

internal interface FeatureConfigEventReceiver : EventReceiver<Event.FeatureConfig>

internal class FeatureConfigEventReceiverImpl internal constructor(
    private val userConfigRepository: UserConfigRepository,
    private val userRepository: UserRepository,
    private val kaliumConfigs: KaliumConfigs,
    private val selfUserId: UserId
) : FeatureConfigEventReceiver {

    override suspend fun onEvent(event: Event.FeatureConfig) {
        handleFeatureConfigEvent(event)
    }

    private suspend fun handleFeatureConfigEvent(event: Event.FeatureConfig) {
        when (event) {
            is Event.FeatureConfig.FileSharingUpdated -> {
                if (kaliumConfigs.fileRestrictionEnabled) {
                    userConfigRepository.setFileSharingStatus(false, null)
                } else {

                    val currentFileSharingStatus: Boolean = userConfigRepository
                        .isFileSharingEnabled()
                        .fold({ false }, { it.isFileSharingEnabled ?: false })

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
                }
            }

            is Event.FeatureConfig.MLSUpdated -> {
                val mlsEnabled = event.model.status == Status.ENABLED
                val selfUserIsWhitelisted = event.model.allowedUsers.contains(selfUserId.toPlainID())
                userConfigRepository.setMLSEnabled(mlsEnabled && selfUserIsWhitelisted)
            }

            is Event.FeatureConfig.ClassifiedDomainsUpdated -> {
                val classifiedDomainsEnabled = event.model.status == Status.ENABLED
                userConfigRepository.setClassifiedDomainsStatus(classifiedDomainsEnabled, event.model.config.domains)
            }

            is Event.FeatureConfig.ConferenceCallingUpdated -> {
                val conferenceCallingEnabled = event.model.status == Status.ENABLED
                userConfigRepository.setConferenceCallingEnabled(conferenceCallingEnabled)
            }

            is Event.FeatureConfig.UnknownFeatureUpdated -> kaliumLogger.w("Ignoring unknown feature config update")
        }
    }
}
