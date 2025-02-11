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
package com.wire.kalium.logic.feature.featureConfig.handler

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.message.TeamSelfDeleteTimer
import com.wire.kalium.logic.data.message.TeamSettingsSelfDeletionStatus
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class SelfDeletingMessagesConfigHandler(
    private val userConfigRepository: UserConfigRepository,
    private val kaliumConfigs: KaliumConfigs
) {
    suspend fun handle(selfDeletingMessagesConfig: SelfDeletingMessagesModel): Either<CoreFailure, Unit> =
        if (!kaliumConfigs.selfDeletingMessages) {
            userConfigRepository.setTeamSettingsSelfDeletionStatus(
                TeamSettingsSelfDeletionStatus(
                    enforcedSelfDeletionTimer = TeamSelfDeleteTimer.Disabled,
                    hasFeatureChanged = null
                )
            )
        } else {
            val selfDeletingMessagesEnabled = selfDeletingMessagesConfig.status == Status.ENABLED
            val enforcedTimeout = selfDeletingMessagesConfig.config
                .enforcedTimeoutSeconds?.toDuration(DurationUnit.SECONDS) ?: Duration.ZERO
            val selfDeletionTimer: TeamSelfDeleteTimer = when {
                selfDeletingMessagesEnabled && enforcedTimeout > Duration.ZERO -> TeamSelfDeleteTimer.Enforced(
                    enforcedTimeout
                )
                selfDeletingMessagesEnabled -> TeamSelfDeleteTimer.Enabled
                else -> TeamSelfDeleteTimer.Disabled
            }
            val hasFeatureChanged = userConfigRepository.getTeamSettingsSelfDeletionStatus().fold(
                {
                    false
                },
                {
                    it.enforcedSelfDeletionTimer != selfDeletionTimer
                }
            )

            userConfigRepository.setTeamSettingsSelfDeletionStatus(
                TeamSettingsSelfDeletionStatus(
                    enforcedSelfDeletionTimer = selfDeletionTimer,
                    hasFeatureChanged = hasFeatureChanged
                )
            )
        }
}
