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
import com.wire.kalium.logic.configuration.E2EISettings
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.AppLockModel
import com.wire.kalium.logic.data.featureConfig.ClassifiedDomainsModel
import com.wire.kalium.logic.data.featureConfig.ConferenceCallingModel
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.E2EIModel
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.selfDeletingMessages.TeamSelfDeleteTimer
import com.wire.kalium.logic.feature.selfDeletingMessages.TeamSettingsSelfDeletionStatus
import com.wire.kalium.logic.feature.user.guestroomlink.GetGuestRoomLinkFeatureStatusUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isNoTeam
import com.wire.kalium.util.DateTimeUtil
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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
    private val getGuestRoomLinkFeatureStatus: GetGuestRoomLinkFeatureStatusUseCase,
    private val kaliumConfigs: KaliumConfigs,
    private val selfUserId: UserId
) : SyncFeatureConfigsUseCase {
    override suspend operator fun invoke(): Either<CoreFailure, Unit> =
        featureConfigRepository.getFeatureConfigs().flatMap {
            // TODO handle other feature flags and after it bump version in [SlowSyncManager.CURRENT_VERSION]
            handleGuestRoomLinkFeatureFlag(it.guestRoomLinkModel)
            handleFileSharingStatus(it.fileSharingModel)
            handleMLSStatus(it.mlsModel)
            handleClassifiedDomainsStatus(it.classifiedDomainsModel)
            handleConferenceCalling(it.conferenceCallingModel)
            handlePasswordChallengeStatus(it.secondFactorPasswordChallengeModel)
            handleSelfDeletingMessagesStatus(it.selfDeletingMessagesModel)
            handleE2EISettings(it.e2EIModel)
            handleAppLock(it.appLockModel)
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

    private fun handleAppLock(appLockModel: AppLockModel) {
        userConfigRepository.setAppLockStatus(appLockModel.config)
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
        val newStatus: Boolean = model.status == Status.ENABLED
        val currentStatus = userConfigRepository.isFileSharingEnabled()
        val isStatusChanged: Boolean = when (currentStatus) {
            is Either.Left -> false
            is Either.Right -> {
                when (currentStatus.value.state) {
                    FileSharingStatus.Value.Disabled -> newStatus
                    FileSharingStatus.Value.EnabledAll -> !newStatus
                    // EnabledSome is a build time flag, so we don't need to check if the server side status have been changed
                    is FileSharingStatus.Value.EnabledSome -> !newStatus
                }
            }
        }
        userConfigRepository.setFileSharingStatus(newStatus, isStatusChanged)
    }

    private fun handleGuestRoomLinkFeatureFlag(model: ConfigsStatusModel) {
        if (!kaliumConfigs.guestRoomLink) {
            userConfigRepository.setGuestRoomStatus(false, null)
        } else {
            val status: Boolean = model.status == Status.ENABLED
            val isStatusChanged = when (getGuestRoomLinkFeatureStatus().isGuestRoomLinkEnabled) {
                null, status -> false
                else -> true
            }
            userConfigRepository.setGuestRoomStatus(status, isStatusChanged)
        }
    }

    private fun handleMLSStatus(featureConfig: MLSModel) {
        val mlsEnabled = featureConfig.status == Status.ENABLED
        val selfUserIsWhitelisted = featureConfig.allowedUsers.contains(selfUserId.toPlainID())
        userConfigRepository.setMLSEnabled(mlsEnabled && selfUserIsWhitelisted)
    }

    private suspend fun handleSelfDeletingMessagesStatus(model: SelfDeletingMessagesModel) {
        if (!kaliumConfigs.selfDeletingMessages) {
            userConfigRepository.setTeamSettingsSelfDeletionStatus(
                TeamSettingsSelfDeletionStatus(
                    enforcedSelfDeletionTimer = TeamSelfDeleteTimer.Disabled,
                    hasFeatureChanged = null
                )
            )
        } else {
            val selfDeletingMessagesEnabled = model.status == Status.ENABLED
            val enforcedTimeout = model.config.enforcedTimeoutSeconds?.toDuration(DurationUnit.SECONDS) ?: ZERO
            val selfDeletionTimer: TeamSelfDeleteTimer = when {
                selfDeletingMessagesEnabled && enforcedTimeout > ZERO -> TeamSelfDeleteTimer.Enforced(enforcedTimeout)
                selfDeletingMessagesEnabled -> TeamSelfDeleteTimer.Enabled
                else -> TeamSelfDeleteTimer.Disabled
            }
            userConfigRepository.setTeamSettingsSelfDeletionStatus(
                TeamSettingsSelfDeletionStatus(
                    enforcedSelfDeletionTimer = selfDeletionTimer,
                    hasFeatureChanged = null, // when syncing the initial status, we don't know if the status changed so we set it to null
                )
            )
        }
    }

    private fun handleE2EISettings(featureConfig: E2EIModel) {
        val gracePeriodEndMs = featureConfig.config.verificationExpirationNS.toDuration(DurationUnit.NANOSECONDS).inWholeMilliseconds
        userConfigRepository.setE2EISettings(
            E2EISettings(
                isRequired = featureConfig.status == Status.ENABLED,
                discoverUrl = featureConfig.config.discoverUrl,
                gracePeriodEnd = Instant.fromEpochMilliseconds(gracePeriodEndMs)
            )
        )
        userConfigRepository.setE2EINotificationTime(DateTimeUtil.currentInstant())
    }
}
