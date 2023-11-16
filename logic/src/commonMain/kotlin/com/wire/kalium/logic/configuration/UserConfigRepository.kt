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

package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.toEntity
import com.wire.kalium.logic.data.featureConfig.toModel
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.toDao
import com.wire.kalium.logic.data.user.toModel
import com.wire.kalium.logic.data.message.SelfDeletionMapper.toSelfDeletionTimerEntity
import com.wire.kalium.logic.data.message.SelfDeletionMapper.toTeamSelfDeleteTimer
import com.wire.kalium.logic.data.message.TeamSettingsSelfDeletionStatus
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.functional.isLeft
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.mapRight
import com.wire.kalium.logic.wrapFlowStorageRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.config.MLSConfigDAO
import com.wire.kalium.persistence.dao.config.model.TeamSettingsSelfDeletionStatusEntity
import com.wire.kalium.persistence.dao.config.UserConfigDAO
import com.wire.kalium.persistence.dao.config.model.IsFileSharingEnabledEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Suppress("TooManyFunctions")
interface UserConfigRepository {
    suspend fun setAppLockStatus(
        isAppLocked: Boolean,
        timeout: Int,
        isStatusChanged: Boolean?
    ): Either<StorageFailure, Unit>

    suspend fun isTeamAppLockEnabled(): Either<StorageFailure, AppLockTeamConfig>
    suspend fun observeAppLockConfig(): Flow<Either<StorageFailure, AppLockTeamConfig>>
    suspend fun setTeamAppLockAsNotified(): Either<StorageFailure, Unit>
    suspend fun setFileSharingStatus(
        status: Boolean,
        isStatusChanged: Boolean?
    ): Either<StorageFailure, Unit>

    suspend fun setFileSharingAsNotified(): Either<StorageFailure, Unit>
    suspend fun isFileSharingEnabled(): Either<StorageFailure, FileSharingStatus>
    suspend fun isFileSharingEnabledFlow(): Flow<Either<StorageFailure, FileSharingStatus>>
    suspend fun setClassifiedDomainsStatus(
        enabled: Boolean,
        domains: List<String>
    ): Either<StorageFailure, Unit>

    suspend fun getClassifiedDomainsStatus(): Flow<Either<StorageFailure, ClassifiedDomainsStatus>>
    suspend fun isMLSEnabled(): Either<StorageFailure, Boolean>
    suspend fun setMLSEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    suspend fun getE2EISettings(): Either<StorageFailure, E2EISettings>
    suspend fun observeE2EISettings(): Flow<Either<StorageFailure, E2EISettings>>
    suspend fun setE2EISettings(setting: E2EISettings): Either<StorageFailure, Unit>
    suspend fun snoozeE2EINotification(duration: Duration): Either<StorageFailure, Unit>
    suspend fun setDefaultProtocol(protocol: SupportedProtocol): Either<StorageFailure, Unit>
    suspend fun getDefaultProtocol(): Either<StorageFailure, SupportedProtocol>
    suspend fun setSupportedProtocols(protocols: Set<SupportedProtocol>): Either<StorageFailure, Unit>
    suspend fun getSupportedProtocols(): Either<StorageFailure, Set<SupportedProtocol>>
    suspend fun setConferenceCallingEnabled(enabled: Boolean): Either<StorageFailure, Unit>
    suspend fun isConferenceCallingEnabled(): Either<StorageFailure, Boolean>
    suspend fun setSecondFactorPasswordChallengeStatus(isRequired: Boolean): Either<StorageFailure, Unit>
    suspend fun isSecondFactorPasswordChallengeRequired(): Either<StorageFailure, Boolean>
    suspend fun isReadReceiptsEnabled(): Flow<Either<StorageFailure, Boolean>>
    suspend fun setReadReceiptsStatus(enabled: Boolean): Either<StorageFailure, Unit>
    suspend fun isTypingIndicatorEnabled(): Flow<Either<StorageFailure, Boolean>>
    suspend fun setTypingIndicatorStatus(enabled: Boolean): Either<StorageFailure, Unit>
    suspend fun setGuestRoomStatus(status: Boolean, isStatusChanged: Boolean?): Either<StorageFailure, Unit>
    suspend fun getGuestRoomLinkStatus(): Either<StorageFailure, GuestRoomLinkStatus>
    suspend fun observeGuestRoomLinkFeatureFlag(): Flow<Either<StorageFailure, GuestRoomLinkStatus>>
    suspend fun setScreenshotCensoringConfig(enabled: Boolean): Either<StorageFailure, Unit>
    suspend fun observeScreenshotCensoringConfig(): Flow<Either<StorageFailure, Boolean>>

    suspend fun getTeamSettingsSelfDeletionStatus(): Either<StorageFailure, TeamSettingsSelfDeletionStatus>
    suspend fun setTeamSettingsSelfDeletionStatus(
        teamSettingsSelfDeletionStatus: TeamSettingsSelfDeletionStatus
    ): Either<StorageFailure, Unit>

    suspend fun markTeamSettingsSelfDeletingMessagesStatusAsNotified(): Either<StorageFailure, Unit>
    suspend fun observeTeamSettingsSelfDeletingStatus(): Flow<Either<StorageFailure, TeamSettingsSelfDeletionStatus>>
    suspend fun observeE2EINotificationTime(): Flow<Either<StorageFailure, Instant?>>
    suspend fun setE2EINotificationTime(instant: Instant): Either<StorageFailure, Unit>
    suspend fun getMigrationConfiguration(): Either<StorageFailure, MLSMigrationModel>
    suspend fun setMigrationConfiguration(configuration: MLSMigrationModel): Either<StorageFailure, Unit>
}

@Suppress("TooManyFunctions")
class UserConfigDataSource(
    private val userConfigDAO: UserConfigDAO,
    private val mlsConfigDAO: MLSConfigDAO,
    private val kaliumConfigs: KaliumConfigs
) : UserConfigRepository {

    override suspend fun setFileSharingStatus(
        status: Boolean,
        isStatusChanged: Boolean?
    ): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigDAO.persistFileSharingStatus(status, isStatusChanged) }

    override suspend fun setFileSharingAsNotified(): Either<StorageFailure, Unit> = wrapStorageRequest {
        userConfigDAO.setFileSharingAsNotified()
    }

    override suspend fun isFileSharingEnabled(): Either<StorageFailure, FileSharingStatus> {
        val serverSideConfig = wrapStorageRequest { userConfigDAO.isFileSharingEnabled() }
        val buildConfig = kaliumConfigs.fileRestrictionState
        return deriveFileSharingStatus(serverSideConfig, buildConfig)
    }

    override suspend fun isFileSharingEnabledFlow(): Flow<Either<StorageFailure, FileSharingStatus>> =
        userConfigDAO.isFileSharingEnabledFlow()
            .wrapStorageRequest()
            .map {
                val buildConfig = kaliumConfigs.fileRestrictionState
                deriveFileSharingStatus(it, buildConfig)
            }

    private suspend fun deriveFileSharingStatus(
        serverSideConfig: Either<StorageFailure, IsFileSharingEnabledEntity>,
        buildConfig: BuildFileRestrictionState
    ): Either<StorageFailure, FileSharingStatus> = when {
        serverSideConfig.isLeft() -> serverSideConfig

        serverSideConfig.value.status.not() -> Either.Right(
            FileSharingStatus(
                isStatusChanged = serverSideConfig.value.isStatusChanged,
                state = FileSharingStatus.Value.Disabled
            )
        )

        buildConfig is BuildFileRestrictionState.AllowSome -> Either.Right(
            FileSharingStatus(
                isStatusChanged = false,
                state = FileSharingStatus.Value.EnabledSome(buildConfig.allowedType)
            )
        )

        buildConfig is BuildFileRestrictionState.NoRestriction -> Either.Right(
            FileSharingStatus(
                isStatusChanged = serverSideConfig.value.isStatusChanged,
                state = FileSharingStatus.Value.EnabledAll
            )
        )

        else -> error("Unknown file restriction state: buildConfig: $buildConfig , serverConfig: $serverSideConfig")
    }

    override suspend fun setClassifiedDomainsStatus(enabled: Boolean, domains: List<String>) =
        wrapStorageRequest { userConfigDAO.persistClassifiedDomainsStatus(enabled, domains) }

    override suspend fun getClassifiedDomainsStatus(): Flow<Either<StorageFailure, ClassifiedDomainsStatus>> =
        userConfigDAO.isClassifiedDomainsEnabledFlow().wrapStorageRequest().map {
            it.map { classifiedDomain ->
                ClassifiedDomainsStatus(classifiedDomain.status, classifiedDomain.trustedDomains)
            }
        }

    override suspend fun isMLSEnabled(): Either<StorageFailure, Boolean> =
        wrapStorageRequest { mlsConfigDAO.isMLSEnabled() }

    override suspend fun setMLSEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { mlsConfigDAO.enableMLS(enabled) }

    override suspend fun getE2EISettings(): Either<StorageFailure, E2EISettings> =
        wrapStorageRequest { mlsConfigDAO.getE2EISettings() }
            .map { E2EISettings.fromEntity(it) }

    override suspend fun observeE2EISettings(): Flow<Either<StorageFailure, E2EISettings>> =
        mlsConfigDAO.e2EISettingsFlow()
            .wrapStorageRequest()
            .mapRight { E2EISettings.fromEntity(it) }

    override suspend fun setE2EISettings(setting: E2EISettings): Either<StorageFailure, Unit> =
        wrapStorageRequest { mlsConfigDAO.setE2EISettings(setting.toEntity()) }

    override suspend fun observeE2EINotificationTime(): Flow<Either<StorageFailure, Instant?>> =
        mlsConfigDAO.e2EINotificationTimeFlow()
            .wrapStorageRequest()
            .mapRight { Instant.fromEpochMilliseconds(it) }

    override suspend fun setE2EINotificationTime(instant: Instant): Either<StorageFailure, Unit> =
        wrapStorageRequest { mlsConfigDAO.setE2EINotificationTime(instant.toEpochMilliseconds()) }

    override suspend fun snoozeE2EINotification(duration: Duration): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            getE2EINotificationTimeOrNull()?.let { current ->
                val notifyUserAfterMs = current.plus(duration.inWholeMilliseconds)
                mlsConfigDAO.setE2EINotificationTime(notifyUserAfterMs)
            }
        }

    private suspend fun getE2EINotificationTimeOrNull() =
        wrapStorageRequest { mlsConfigDAO.getE2EINotificationTime() }.getOrNull()

    override suspend fun setDefaultProtocol(protocol: SupportedProtocol): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigDAO.persistDefaultProtocol(protocol.toDao()) }

    override suspend fun getDefaultProtocol(): Either<StorageFailure, SupportedProtocol> =
        wrapStorageRequest { userConfigDAO.defaultProtocol().toModel() }

    override suspend fun setSupportedProtocols(protocols: Set<SupportedProtocol>): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigDAO.setSupportedProtocols(protocols.toDao()) }

    override suspend fun getSupportedProtocols(): Either<StorageFailure, Set<SupportedProtocol>> =
        wrapStorageRequest { userConfigDAO.getSupportedProtocols()?.toModel() }

    override suspend fun setConferenceCallingEnabled(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigDAO.persistConferenceCalling(enabled)
        }

    override suspend fun isConferenceCallingEnabled(): Either<StorageFailure, Boolean> =
        wrapStorageRequest {
            userConfigDAO.isConferenceCallingEnabled()
        }

    override suspend fun setSecondFactorPasswordChallengeStatus(isRequired: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigDAO.persistSecondFactorPasswordChallengeStatus(isRequired)
        }

    override suspend fun isSecondFactorPasswordChallengeRequired(): Either<StorageFailure, Boolean> =
        wrapStorageRequest {
            userConfigDAO.isSecondFactorPasswordChallengeRequired()
        }

    override suspend fun isReadReceiptsEnabled(): Flow<Either<StorageFailure, Boolean>> =
        userConfigDAO.areReadReceiptsEnabled().wrapStorageRequest()

    override suspend fun setReadReceiptsStatus(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigDAO.persistReadReceipts(enabled)
        }

    override suspend fun isTypingIndicatorEnabled(): Flow<Either<StorageFailure, Boolean>> =
        userConfigDAO.isTypingIndicatorEnabled().wrapStorageRequest()

    override suspend fun setTypingIndicatorStatus(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigDAO.persistTypingIndicator(enabled)
        }

    override suspend fun setGuestRoomStatus(
        status: Boolean,
        isStatusChanged: Boolean?
    ): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigDAO.persistGuestRoomLinkFeatureFlag(status, isStatusChanged)
        }

    override suspend fun getGuestRoomLinkStatus(): Either<StorageFailure, GuestRoomLinkStatus> =
        wrapStorageRequest { userConfigDAO.isGuestRoomLinkEnabled() }.map {
            with(it) { GuestRoomLinkStatus(status, isStatusChanged) }
        }

    override suspend fun observeGuestRoomLinkFeatureFlag(): Flow<Either<StorageFailure, GuestRoomLinkStatus>> =
        userConfigDAO.isGuestRoomLinkEnabledFlow()
            .wrapStorageRequest()
            .map {
                it.map { isGuestRoomLinkEnabledEntity ->
                    GuestRoomLinkStatus(
                        isGuestRoomLinkEnabledEntity.status,
                        isGuestRoomLinkEnabledEntity.isStatusChanged
                    )
                }
            }

    override suspend fun getTeamSettingsSelfDeletionStatus(): Either<StorageFailure, TeamSettingsSelfDeletionStatus> =
        wrapStorageRequest {
            userConfigDAO.getTeamSettingsSelfDeletionStatus()
        }.map {
            with(it) {
                TeamSettingsSelfDeletionStatus(
                    hasFeatureChanged = isStatusChanged,
                    enforcedSelfDeletionTimer = selfDeletionTimerEntity.toTeamSelfDeleteTimer()
                )
            }
        }

    override suspend fun setTeamSettingsSelfDeletionStatus(teamSettingsSelfDeletionStatus: TeamSettingsSelfDeletionStatus):
            Either<StorageFailure, Unit> =
        wrapStorageRequest {
            val teamSettingsSelfDeletionStatusEntity = TeamSettingsSelfDeletionStatusEntity(
                selfDeletionTimerEntity = teamSettingsSelfDeletionStatus.enforcedSelfDeletionTimer.toSelfDeletionTimerEntity(),
                isStatusChanged = teamSettingsSelfDeletionStatus.hasFeatureChanged,
            )
            userConfigDAO.setTeamSettingsSelfDeletionStatus(teamSettingsSelfDeletionStatusEntity)
        }

    override suspend fun markTeamSettingsSelfDeletingMessagesStatusAsNotified(): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigDAO.markTeamSettingsSelfDeletingMessagesStatusAsNotified()
        }

    override suspend fun observeTeamSettingsSelfDeletingStatus(): Flow<Either<StorageFailure, TeamSettingsSelfDeletionStatus>> =
        userConfigDAO.observeTeamSettingsSelfDeletingStatus().wrapStorageRequest().map {
            it.map {
                TeamSettingsSelfDeletionStatus(
                    hasFeatureChanged = it.isStatusChanged,
                    enforcedSelfDeletionTimer = it.selfDeletionTimerEntity.toTeamSelfDeleteTimer()
                )
            }
        }

    override suspend fun setScreenshotCensoringConfig(enabled: Boolean): Either<StorageFailure, Unit> =
        wrapStorageRequest { userConfigDAO.persistScreenshotCensoring(enabled) }

    override suspend fun observeScreenshotCensoringConfig(): Flow<Either<StorageFailure, Boolean>> =
        userConfigDAO.isScreenshotCensoringEnabledFlow().wrapStorageRequest()

    override suspend fun setAppLockStatus(
        isAppLocked: Boolean,
        timeout: Int,
        isStatusChanged: Boolean?
    ): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigDAO.persistAppLockStatus(
                isAppLocked,
                timeout,
                isStatusChanged
            )
        }

    override suspend fun observeAppLockConfig(): Flow<Either<StorageFailure, AppLockTeamConfig>> =
        wrapFlowStorageRequest {
            userConfigDAO.appLockFlow().map {
                it?.let { config ->
                    AppLockTeamConfig(
                        isEnabled = config.enforceAppLock,
                        timeout = config.inactivityTimeoutSecs.seconds,
                        isStatusChanged = config.isStatusChanged
                    )
                }
            }
        }

    override suspend fun isTeamAppLockEnabled(): Either<StorageFailure, AppLockTeamConfig> {
        val serverSideConfig = wrapStorageRequest { userConfigDAO.appLockStatus() }
        return serverSideConfig.map {
            AppLockTeamConfig(
                isEnabled = it.enforceAppLock,
                timeout = it.inactivityTimeoutSecs.seconds,
                isStatusChanged = it.isStatusChanged
            )
        }
    }

    override suspend fun setTeamAppLockAsNotified(): Either<StorageFailure, Unit> = wrapStorageRequest {
        userConfigDAO.setTeamAppLockAsNotified()
    }

    override suspend fun getMigrationConfiguration(): Either<StorageFailure, MLSMigrationModel> =
        wrapStorageRequest {
            userConfigDAO.getMigrationConfiguration()?.toModel()
        }

    override suspend fun setMigrationConfiguration(configuration: MLSMigrationModel): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            userConfigDAO.setMigrationConfiguration(configuration.toEntity())
        }
}
