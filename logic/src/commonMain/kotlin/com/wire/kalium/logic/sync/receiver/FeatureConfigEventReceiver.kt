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
import com.wire.kalium.logic.configuration.MLSEnablingSetting
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventLoggingStatus
import com.wire.kalium.logic.data.event.logEventProcessing
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.selfDeletingMessages.SelfDeletionTimer.Companion.SELF_DELETION_LOG_TAG
import com.wire.kalium.logic.feature.selfDeletingMessages.TeamSelfDeleteTimer
import com.wire.kalium.logic.feature.selfDeletingMessages.TeamSettingsSelfDeletionStatus
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.serialization.toJsonElement
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal interface FeatureConfigEventReceiver : EventReceiver<Event.FeatureConfig>

internal class FeatureConfigEventReceiverImpl internal constructor(
    private val userConfigRepository: UserConfigRepository,
    private val kaliumConfigs: KaliumConfigs,
    private val selfUserId: UserId
) : FeatureConfigEventReceiver {

    override suspend fun onEvent(event: Event.FeatureConfig) {
        handleFeatureConfigEvent(event)
    }

    @Suppress("LongMethod", "ComplexMethod")
    private suspend fun handleFeatureConfigEvent(event: Event.FeatureConfig) {
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
                handleSelfDeletingFeatureConfig(event.model)

                kaliumLogger.logEventProcessing(
                    EventLoggingStatus.SUCCESS,
                    event,
                    Pair("isDurationEnforced", (event.model.config.enforcedTimeoutSeconds ?: 0) > 0),
                )
            }

            is Event.FeatureConfig.MLSE2EIdUpdated -> {
                val enablingDeadlineMs = event.model.config.verificationExpirationNS.toDuration(DurationUnit.NANOSECONDS).inWholeMilliseconds
                userConfigRepository.setMLSE2EIdSetting(
                    MLSEnablingSetting(
                        status = event.model.status == Status.ENABLED,
                        discoverUrl = event.model.config.discoverUrl,
                        notifyUserAfter = DateTimeUtil.currentInstant(),
                        enablingDeadline = Instant.fromEpochMilliseconds(enablingDeadlineMs)
                    )
                )

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

    private suspend fun handleSelfDeletingFeatureConfig(model: SelfDeletingMessagesModel) {
        if (!kaliumConfigs.selfDeletingMessages) {
            userConfigRepository.setTeamSettingsSelfDeletionStatus(
                TeamSettingsSelfDeletionStatus(
                    enforcedSelfDeletionTimer = TeamSelfDeleteTimer.Disabled,
                    hasFeatureChanged = null
                )
            )
        } else {
            val storedTeamSettingsSelfDeletionStatus = userConfigRepository.getTeamSettingsSelfDeletionStatus().fold({
                TeamSettingsSelfDeletionStatus(hasFeatureChanged = null, enforcedSelfDeletionTimer = TeamSelfDeleteTimer.Enabled)
            }, {
                it
            })
            val selfDeletingMessagesEnabled = model.status == Status.ENABLED
            val enforcedTimeout = model.config.enforcedTimeoutSeconds?.toDuration(DurationUnit.SECONDS) ?: ZERO
            val newTeamSettingsTimer: TeamSelfDeleteTimer = when {
                selfDeletingMessagesEnabled && enforcedTimeout > ZERO -> TeamSelfDeleteTimer.Enforced(enforcedTimeout)
                selfDeletingMessagesEnabled -> TeamSelfDeleteTimer.Enabled
                else -> TeamSelfDeleteTimer.Disabled
            }
            userConfigRepository.setTeamSettingsSelfDeletionStatus(
                TeamSettingsSelfDeletionStatus(
                    enforcedSelfDeletionTimer = newTeamSettingsTimer,
                    // If there is an error fetching the previously stored value, we will always override it and mark it as changed
                    hasFeatureChanged = storedTeamSettingsSelfDeletionStatus.hasFeatureChanged == null
                            || storedTeamSettingsSelfDeletionStatus.enforcedSelfDeletionTimer != newTeamSettingsTimer
                )
            ).onFailure {
                val logMap = mapOf(
                    "value" to newTeamSettingsTimer.toLogMap(eventDescription = "Team Settings Self Deletion Update Failure"),
                    "errorInfo" to "$it"
                ).toJsonElement()
                kaliumLogger.e("$SELF_DELETION_LOG_TAG: $logMap")
            }.onSuccess {
                val logMap = newTeamSettingsTimer.toLogMap(eventDescription = "Team Settings Self Deletion Update Success")
                kaliumLogger.d("$SELF_DELETION_LOG_TAG: ${logMap.toJsonElement()}")
            }
        }
    }
}
